package com.illiamelnyk.studio.core.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * These paddings without the top one: for a list below a screen's own top bar, which already
 * takes the top padding.
 */
@Composable
@ReadOnlyComposable
fun PaddingValues.withoutTop(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}
