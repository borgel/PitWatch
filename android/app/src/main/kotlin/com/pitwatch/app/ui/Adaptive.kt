package com.pitwatch.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material's "medium" width: from here a window is wide enough for side-by-side panes and a navigation rail. */
val WIDE_MIN_WIDTH: Dp = 600.dp

/** Forms and settings stop growing here, so their rows don't stretch across a tablet. */
val READABLE_MAX_WIDTH: Dp = 640.dp

/**
 * Wide windows: open foldables, tablets, phones held sideways, wide split-screen. Decided by the window's width
 * (it follows folding, rotation and resizing), never by orientation.
 */
@Composable
fun isWideWindow(): Boolean = LocalConfiguration.current.screenWidthDp.dp >= WIDE_MIN_WIDTH

/** Centers [content] at no more than [READABLE_MAX_WIDTH]; on a phone that's the whole width. */
@Composable
fun ReadableWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = READABLE_MAX_WIDTH)) { content() }
    }
}
