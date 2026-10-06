package com.pitwatch.app.ui.pitmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.PitMap

sealed interface PitMapState {
    data object Loading : PitMapState
    data object NoKey : PitMapState
    data object Unavailable : PitMapState
    data class Loaded(val map: PitMap) : PitMapState
}

@Composable
fun PitMapScreen(container: AppContainer, config: UserConfig, onOpenSettings: () -> Unit) {
    var state by remember { mutableStateOf<PitMapState>(PitMapState.Loading) }
    // Refetch when the event changes (auto-detected or picked), not just when the key does.
    val eventKey by remember { container.stores.cache.data.map { it.event?.key }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(config.nexusApiKey, eventKey) {
        state = PitMapState.Loading
        state = if (!config.isNexusConfigured) {
            PitMapState.NoKey
        } else {
            container.repository.pitMap()?.let { PitMapState.Loaded(it) } ?: PitMapState.Unavailable
        }
    }
    PitMapContent(state, config.teamNumber?.toString(), onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitMapContent(state: PitMapState, teamNumber: String?, onOpenSettings: () -> Unit) {
    val ours = (state as? PitMapState.Loaded)?.let { PitMapGeometry.focus(it.map, teamNumber) }
    Scaffold(topBar = { TopAppBar(title = { Text(ours?.let { "Pit ${it.address}" } ?: "Pit map") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                PitMapState.Loading -> CircularProgressIndicator()
                PitMapState.NoKey -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Add a FRC Nexus API key in Settings to see the pit map.", Modifier.padding(24.dp))
                    OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
                PitMapState.Unavailable -> Text("This event has no pit map on Nexus.", Modifier.padding(24.dp))
                is PitMapState.Loaded -> PitMapCanvas(state.map, ours)
            }
        }
    }
}

/** A label laid out once per map (not per frame), centered in its box. */
private class MapLabel(val box: Rect, val layout: TextLayoutResult)

@Composable
private fun PitMapCanvas(map: PitMap, ours: PitMap.AssignedPit?) {
    var zoom by remember(map) { mutableFloatStateOf(1f) }
    var pan by remember(map) { mutableStateOf(Offset.Zero) }
    var fit by remember(map) { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { centroid: Offset, zoomChange: Float, panChange: Offset, _: Float ->
        // Zoom around the pinch point, then apply the drag.
        val newZoom = (zoom * zoomChange).coerceIn(0.5f, 8f)
        val applied = newZoom / zoom
        pan = centroid - (centroid - pan) * applied + panChange
        zoom = newZoom
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme

    fun layout(text: String, box: Rect, color: Color): MapLabel {
        var px = PitMapGeometry.labelFontPx(box, text)
        // The estimate assumes digit widths; shrink to the measured width for wider (letter) labels.
        val measured = measurer.measure(text, TextStyle(fontSize = with(density) { px.toSp() }), maxLines = 1).size.width
        if (measured > box.width * 0.9f) px *= box.width * 0.9f / measured
        val style = TextStyle(color = color, fontSize = with(density) { px.toSp() })
        return MapLabel(box, measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Clip, constraints = Constraints(maxWidth = box.width.toInt().coerceAtLeast(1))))
    }

    val labels = remember(map, ours, colors, density) {
        map.areas.orEmpty().values.map { layout(it.label, PitMapGeometry.rect(it.position, it.size), colors.onSurfaceVariant) } +
            map.labels.orEmpty().values.map { layout(it.label, PitMapGeometry.rect(it.position, it.size), colors.onSurface) } +
            map.pits.mapNotNull { (address, pit) ->
                val color = if (address == ours?.address) colors.onPrimary else colors.onSecondaryContainer
                pit.team?.let { layout(it, PitMapGeometry.rect(pit.position, pit.size), color) }
            }
    }

    Canvas(
        Modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { size ->
                // Fit the whole map, then open zoomed in on our pit (once per map, outside the draw pass).
                fit = PitMapGeometry.fitScale(map, size.width.toFloat(), size.height.toFloat())
                if (ours != null && zoom == 1f && pan == Offset.Zero) {
                    zoom = 2.5f
                    val pit = PitMapGeometry.rect(ours.pit.position, ours.pit.size).center
                    pan = Offset(size.width / 2f - pit.x * fit * zoom, size.height / 2f - pit.y * fit * zoom)
                }
            }
            .transformable(transform),
    ) {
        val scale = fit * zoom
        translate(pan.x, pan.y) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                map.areas?.values?.forEach { area ->
                    val r = PitMapGeometry.rect(area.position, area.size)
                    drawRect(colors.surfaceVariant, r.topLeft, r.size)
                }
                map.walls?.values?.forEach { wall ->
                    val r = PitMapGeometry.rect(wall.position, wall.size)
                    drawRect(colors.outline, r.topLeft, r.size)
                }
                map.arrows?.values?.forEach { arrow ->
                    val (tail, head) = PitMapGeometry.arrowLine(arrow)
                    val width = (arrow.size.x / 6).toFloat().coerceAtLeast(2f)
                    drawLine(colors.outline, tail, head, strokeWidth = width, cap = StrokeCap.Round)
                    // A small head: two strokes back from the tip.
                    val back = (tail - head) / (tail - head).getDistance().coerceAtLeast(1f) * (width * 3)
                    val side = Offset(-back.y, back.x) * 0.6f
                    drawLine(colors.outline, head, head + back + side, strokeWidth = width, cap = StrokeCap.Round)
                    drawLine(colors.outline, head, head + back - side, strokeWidth = width, cap = StrokeCap.Round)
                }
                map.pits.forEach { (address, pit) ->
                    val r = PitMapGeometry.rect(pit.position, pit.size)
                    drawRect(if (address == ours?.address) colors.primary else colors.secondaryContainer, r.topLeft, r.size)
                    drawRect(colors.outline, r.topLeft, r.size, style = Stroke(width = 1f))
                }
                labels.forEach { label ->
                    drawText(label.layout, topLeft = label.box.center - Offset(label.layout.size.width / 2f, label.layout.size.height / 2f))
                }
            }
        }
    }
}
