package com.illiamelnyk.studio.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.core.ui.theme.ArtPalette
import com.illiamelnyk.studio.core.ui.theme.CoverGradient
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cover art drawn by the app: one of the palette's gradients, picked by [seed] (a track's title or
 * id) together with its direction, so a track always gets the same cover.
 *
 * @param size one of `StudioTheme.dimens.size.cover`, or an avatar size.
 * @param shape by default the radius tokens.json gives covers of this size.
 * @param generating makes the cover "living": the gradient drifts while the track is being composed.
 */
@Composable
fun Cover(
    seed: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CoverDefaults.shape(size),
    generating: Boolean = false,
) {
    val drift = if (generating) rememberDrift() else null
    Box(
        modifier
            .size(size)
            .clip(shape)
            .coverArt(seed, drift?.let { state -> { state.value } }),
    )
}

object CoverDefaults {
    /** The radius for a cover of [size]: `xs` up to 40 dp, `s` up to 56 dp and `m` above. */
    @Composable
    @ReadOnlyComposable
    fun shape(size: Dp): Shape {
        val covers = StudioTheme.dimens.size.cover
        val shapes = StudioTheme.shapes
        return when {
            size <= covers.xs -> shapes.xs
            size <= covers.m -> shapes.s
            else -> shapes.m
        }
    }
}

/**
 * Paints the cover art for [seed] behind the content.
 *
 * @param drift `null` for a still cover; otherwise read on every frame, from 0 to 1: the gradient is
 *   three times the size of the cover and pans across it, as `.living` does in the kit.
 */
internal fun Modifier.coverArt(seed: String, drift: (() -> Float)? = null): Modifier = drawWithCache {
    val hash = seed.hashCode()
    val gradient = ArtPalette.coverGradients[Math.floorMod(hash, ArtPalette.coverGradients.size)]
    // Turning the gradient by a quarter, picked by other bits of the hash, gives four covers per gradient.
    val angle = gradient.angle + QUARTER_TURN * Math.floorMod(hash / ArtPalette.coverGradients.size, 4)
    val still = gradient.brush(angle, Rect(Offset.Zero, size))
    onDrawBehind {
        val brush = if (drift == null) {
            still
        } else {
            val position = drift()
            val width = size.width * LIVING_SCALE
            val height = size.height * LIVING_SCALE
            // background-position moves from 0 % 40 % to 100 % 60 %.
            val left = (size.width - width) * position
            val top = (size.height - height) * (LIVING_TOP_FROM + (LIVING_TOP_TO - LIVING_TOP_FROM) * position)
            gradient.brush(angle, Rect(left, top, left + width, top + height))
        }
        drawRect(brush)
    }
}

@Composable
private fun rememberDrift(): State<Float> {
    val motion = StudioTheme.motion
    return rememberInfiniteTransition(label = "livingCover").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // There and back again in one period.
        animationSpec = infiniteRepeatable(
            tween(motion.loop.livingCover / 2, easing = motion.loopEasing),
            RepeatMode.Reverse,
        ),
        label = "drift",
    )
}

/**
 * The CSS `linear-gradient(angle, …)` over [box]: the gradient line goes through the center in the
 * direction of the angle and is long enough for the corners to get the end colors.
 */
private fun CoverGradient.brush(angle: Float, box: Rect): Brush {
    val radians = Math.toRadians(angle.toDouble())
    val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
    val halfLength = (abs(box.width * direction.x) + abs(box.height * direction.y)) / 2
    return Brush.linearGradient(
        0f to start,
        middleStop to middle,
        1f to end,
        start = box.center - direction * halfLength,
        end = box.center + direction * halfLength,
    )
}

private const val QUARTER_TURN = 90f
private const val LIVING_SCALE = 3f
private const val LIVING_TOP_FROM = 0.4f
private const val LIVING_TOP_TO = 0.6f

@PreviewLightDark
@Composable
private fun CoverPreview() {
    PreviewSurface {
        val covers = StudioTheme.dimens.size.cover
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
            Cover("Night Drive Through Kyiv", covers.xs)
            Cover("Rust and Redemption", covers.s)
            Cover("Lo-fi rainy morning", covers.m, generating = true)
        }
        Cover("Light Through Clouds", covers.l)
    }
}
