package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.LocalStudioContentColor
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Indeterminate circular progress, 16 dp: a quarter arc in [color] spinning over a ring in
 * [trackColor]. Used inside buttons and other small controls.
 *
 * @param trackColor `surface.level4` as in the kit; [Color.Transparent] where that wouldn't stand out
 *   from the arc, such as on a primary button.
 */
@Composable
fun StudioSpinner(
    modifier: Modifier = Modifier,
    color: Color = LocalStudioContentColor.current,
    trackColor: Color = StudioTheme.colors.surface.level4,
) {
    val dimens = StudioTheme.dimens
    val period = StudioTheme.motion.loop.spinner
    val rotation by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "rotation",
    )
    Canvas(modifier.progressSemantics().size(dimens.size.icon.s)) {
        val stroke = Stroke(width = dimens.stroke.thick.toPx())
        val topLeft = Offset(stroke.width / 2, stroke.width / 2)
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(trackColor, 0f, FULL_TURN, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        // The top quarter of the ring, like the colored top border of the kit's CSS spinner.
        rotate(rotation) {
            drawArc(color, -135f, FULL_TURN / 4, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        }
    }
}

/**
 * Indeterminate linear progress across the available width: a `content.primary` bar sliding over a
 * 4 dp `surface.level4` track.
 */
@Composable
fun StudioLinearProgress(modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val motion = StudioTheme.motion
    val position by rememberInfiniteTransition(label = "linearProgress").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(motion.loop.progress, easing = motion.loopEasing)),
        label = "position",
    )
    Canvas(
        modifier
            .progressSemantics()
            .fillMaxWidth()
            .height(StudioTheme.dimens.size.component.seekTrack)
            .clip(StudioTheme.shapes.full),
    ) {
        drawRect(colors.surface.level4)
        val barWidth = size.width * BAR_FRACTION
        // As in the kit: the bar moves from one bar width before the track to 2.3 widths into it.
        val x = barWidth * (BAR_START + (BAR_END - BAR_START) * position)
        drawRect(colors.content.primary, topLeft = Offset(x, 0f), size = Size(barWidth, size.height))
    }
}

private const val FULL_TURN = 360f
private const val BAR_FRACTION = 0.45f
private const val BAR_START = -1f
private const val BAR_END = 2.3f

@PreviewLightDark
@Composable
private fun StudioProgressPreview() {
    PreviewSurface {
        StudioSpinner()
        StudioLinearProgress()
    }
}
