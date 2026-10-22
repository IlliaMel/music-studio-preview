package com.illiamelnyk.studio.feature.player.presentation.host

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The shape of the cover that travels between the players: the mini player's [smallShape] at
 * [smallSize] and below, the full player's [largeShape] from [largeSize] up, and in between a radius
 * that follows the size. The shape follows the size alone, so the cover morphs smoothly whichever
 * way it travels.
 */
@Immutable
internal data class PlayerCoverShape(
    val smallSize: Dp,
    val smallShape: CornerBasedShape,
    val largeSize: Dp,
    val largeShape: CornerBasedShape,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = size.minDimension
        val small = with(density) { smallSize.toPx() }
        val large = with(density) { largeSize.toPx() }
        val growth = if (large > small) ((side - small) / (large - small)).coerceIn(0f, 1f) else 1f
        val radius = lerp(
            smallShape.topStart.toPx(size, density),
            largeShape.topStart.toPx(size, density),
            growth,
        ).coerceAtMost(side / 2)
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius)))
    }
}

/** The cover's shape from the theme: the mini player's cover size and radius, and the large cover's. */
@Composable
@ReadOnlyComposable
internal fun playerCoverShape(): PlayerCoverShape {
    val covers = StudioTheme.dimens.size.cover
    val shapes = StudioTheme.shapes
    return PlayerCoverShape(smallSize = covers.xs, smallShape = shapes.s, largeSize = covers.l, largeShape = shapes.l)
}
