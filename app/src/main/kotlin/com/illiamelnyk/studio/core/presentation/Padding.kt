package com.illiamelnyk.studio.core.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * These paddings without the top one: for a list below a screen's own top bar, which already
 * takes the top padding. The rest is read when the list lays out, like the original's.
 */
fun PaddingValues.withoutTop(): PaddingValues = WithoutTop(this)

@Immutable
private data class WithoutTop(private val padding: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = padding.calculateBottomPadding()
}
