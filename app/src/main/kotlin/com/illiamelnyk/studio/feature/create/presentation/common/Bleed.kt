package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Lets the content run [horizontal] past both sides of the width its parent gives it: a row that
 * scrolls sideways then slides under the screen edges instead of stopping at the parent's padding,
 * which shows that there is more. Give the scrolling content the same padding, so its first and
 * last items still line up with the rest of the screen.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2),
    )
    val width = (placeable.width - extra * 2).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}
