package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.theme.ArtPalette
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.hypot
import kotlin.math.max

/**
 * The 64 dp glowing blue sphere that means "the AI is working". While [active] it slowly turns.
 * It is decorative: whatever shows it also says in words what is happening.
 */
@Composable
fun Orb(modifier: Modifier = Modifier, active: Boolean = true) {
    val rotation = if (active) rememberRotation() else null
    val shape = StudioTheme.shapes.full
    Canvas(
        modifier
            .size(StudioTheme.dimens.size.avatar.l)
            .dropShadow(shape, Shadow(radius = StudioTheme.dimens.spacing.xxl, color = ArtPalette.orbGlow))
            .clip(shape),
    ) {
        rotate(rotation?.value ?: 0f) {
            drawRect(ArtPalette.orbBase)
            // The kit's radial gradients, from the bottom layer to the top one.
            drawSpot(ArtPalette.orbLight, centerX = 0.26f, centerY = 0.76f, reach = 0.5f)
            drawSpot(ArtPalette.orbDeep, centerX = 0.7f, centerY = 0.72f, reach = 0.55f)
            drawSpot(ArtPalette.orbHighlight, centerX = 0.32f, centerY = 0.28f, reach = 0.24f)
        }
    }
}

@Composable
private fun rememberRotation(): State<Float> = rememberInfiniteTransition(label = "orb").animateFloat(
    initialValue = 0f,
    targetValue = FULL_TURN,
    animationSpec = infiniteRepeatable(tween(StudioTheme.motion.loop.orb, easing = LinearEasing)),
    label = "rotation",
)

/**
 * CSS `radial-gradient(circle at x y, color 0, transparent reach)`: the gradient ray runs to the
 * farthest corner, and the color fades out over [reach] of it.
 */
private fun DrawScope.drawSpot(color: Color, centerX: Float, centerY: Float, reach: Float) {
    val center = Offset(size.width * centerX, size.height * centerY)
    val farthestCorner = hypot(max(center.x, size.width - center.x), max(center.y, size.height - center.y))
    drawRect(
        Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = farthestCorner * reach,
        ),
    )
}

private const val FULL_TURN = 360f

@PreviewLightDark
@Composable
private fun OrbPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xxl)) {
            Orb()
            Orb(active = false)
        }
    }
}
