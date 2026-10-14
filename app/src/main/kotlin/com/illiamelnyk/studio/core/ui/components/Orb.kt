package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.lerp
import com.illiamelnyk.studio.core.ui.theme.ArtPalette
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.hypot
import kotlin.math.max

/**
 * The 64 dp glowing blue sphere that means "the AI is working". While [active] it slowly turns and
 * its glow breathes (`glow.orb`); at rest the glow stays at its quietest. It is decorative: whatever
 * shows it also says in words what is happening.
 */
@Composable
fun Orb(modifier: Modifier = Modifier, active: Boolean = true) {
    val rotation = if (active) rememberLoop(StudioTheme.motion.loop.orb, label = "rotation") else null
    val breath = if (active) rememberBreath() else null
    val glow = StudioTheme.effects.glow.orb
    val shape = StudioTheme.shapes.full
    Canvas(
        modifier
            .size(StudioTheme.dimens.size.avatar.l)
            // The block runs in the draw phase, so the breathing glow doesn't recompose the orb.
            .dropShadow(shape) {
                val fraction = breath?.value ?: 0f
                radius = lerp(glow.blurMin, glow.blurMax, fraction).toPx()
                alpha = glow.alphaMin + (glow.alphaMax - glow.alphaMin) * fraction
                color = glow.color
            }
            .clip(shape),
    ) {
        rotate((rotation?.value ?: 0f) * FULL_TURN) {
            drawRect(ArtPalette.orbBase)
            // The kit's radial gradients, from the bottom layer to the top one.
            drawSpot(ArtPalette.orbLight, centerX = 0.26f, centerY = 0.76f, reach = 0.5f)
            drawSpot(ArtPalette.orbDeep, centerX = 0.7f, centerY = 0.72f, reach = 0.55f)
            drawSpot(ArtPalette.orbHighlight, centerX = 0.32f, centerY = 0.28f, reach = 0.24f)
        }
    }
}

/** Goes from 0 to 1 once per [period], over and over. */
@Composable
private fun rememberLoop(period: Int, label: String): State<Float> =
    rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = label,
    )

/** Goes from 0 to 1 and back once per `glowBreath`, easing in and out. */
@Composable
private fun rememberBreath(): State<Float> {
    val motion = StudioTheme.motion
    return rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(motion.loop.glowBreath / 2, easing = motion.loopEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
}

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
