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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
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
fun PitMapScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = UserConfig())
    var state by remember { mutableStateOf<PitMapState>(PitMapState.Loading) }
    LaunchedEffect(config.nexusApiKey) {
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

@Composable
private fun PitMapCanvas(map: PitMap, ours: PitMap.AssignedPit?) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var centered by remember { mutableStateOf(false) }
    val transform = rememberTransformableState { centroid: Offset, zoomChange: Float, panChange: Offset, _: Float ->
        // Zoom around the pinch point, then apply the drag.
        val newZoom = (zoom * zoomChange).coerceIn(0.5f, 8f)
        val applied = newZoom / zoom
        pan = centroid - (centroid - pan) * applied + panChange
        zoom = newZoom
    }
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize().transformable(transform)) {
        /** Draws [text] sized in map units to fit [box], centered, one line. */
        fun DrawScope.label(text: String, box: androidx.compose.ui.geometry.Rect, color: androidx.compose.ui.graphics.Color) {
            var px = PitMapGeometry.labelFontPx(box, text)
            // The estimate assumes digit widths; shrink to the measured width for wider (letter) labels.
            val measured = measurer.measure(text, TextStyle(fontSize = px.toSp()), maxLines = 1).size.width
            if (measured > box.width * 0.9f) px *= box.width * 0.9f / measured
            val style = TextStyle(color = color, fontSize = px.toSp())
            val layout = measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = box.width.toInt().coerceAtLeast(1)))
            drawText(layout, topLeft = box.center - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        val fit = PitMapGeometry.fitScale(map, size.width, size.height)
        if (!centered && ours != null) {
            // Open zoomed in on our pit.
            zoom = 2.5f
            val pit = PitMapGeometry.rect(ours.pit.position, ours.pit.size).center
            pan = Offset(size.width / 2 - pit.x * fit * zoom, size.height / 2 - pit.y * fit * zoom)
            centered = true
        }
        val scale = fit * zoom
        translate(pan.x, pan.y) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                map.areas?.values?.forEach { area ->
                    val r = PitMapGeometry.rect(area.position, area.size)
                    drawRect(colors.surfaceVariant, r.topLeft, r.size)
                    label(area.label, r, colors.onSurfaceVariant)
                }
                map.walls?.values?.forEach { wall ->
                    val r = PitMapGeometry.rect(wall.position, wall.size)
                    drawRect(colors.outline, r.topLeft, r.size)
                }
                map.arrows?.values?.forEach { arrow ->
                    val r = PitMapGeometry.rect(arrow.position, arrow.size)
                    drawRect(colors.outlineVariant, r.topLeft, r.size, style = Stroke(width = 2f))
                }
                map.labels?.values?.forEach { label ->
                    val r = PitMapGeometry.rect(label.position, label.size)
                    label(label.label, r, colors.onSurface)
                }
                map.pits.forEach { (address, pit) ->
                    val r = PitMapGeometry.rect(pit.position, pit.size)
                    val isOurs = address == ours?.address
                    drawRect(if (isOurs) colors.primary else colors.secondaryContainer, r.topLeft, r.size)
                    drawRect(colors.outline, r.topLeft, r.size, style = Stroke(width = 1f))
                    pit.team?.let { label(it, r, if (isOurs) colors.onPrimary else colors.onSecondaryContainer) }
                }
            }
        }
    }
}
