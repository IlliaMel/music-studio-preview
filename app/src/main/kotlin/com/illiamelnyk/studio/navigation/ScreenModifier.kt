package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * How every destination is placed: it fills the display on an opaque [background], so a screen
 * sliding over another hides it, and consumes [contentPadding], so its own `imePadding()` adds only
 * what the keyboard covers beyond the bars.
 */
internal fun Modifier.destination(background: Color, contentPadding: PaddingValues): Modifier = this
    .fillMaxSize()
    .background(background)
    .consumeWindowInsets(contentPadding)
