package com.illiamelnyk.studio.core.ui.foundation

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Content that glass elements drawn over it can blur, such as a scrolling list under the mini
 * player. It wraps Haze, so screens don't depend on it.
 *
 * Mark the content with [Modifier.glassBackdrop] and make it available to the glass elements above it
 * with [ProvideGlassBackdrop].
 */
@Stable
class GlassBackdrop internal constructor(internal val hazeState: HazeState)

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val hazeState = rememberHazeState()
    return remember(hazeState) { GlassBackdrop(hazeState) }
}

/** Marks this content as what the glass elements of [backdrop] blur. */
fun Modifier.glassBackdrop(backdrop: GlassBackdrop): Modifier = hazeSource(backdrop.hazeState)

/**
 * The backdrop that glass elements, such as the glass [StudioButton][com.illiamelnyk.studio.core.ui.components.StudioButton]
 * or the mini player, blur. Without one they show the plain glass tint.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Makes [backdrop] the backdrop of the glass elements in [content]. */
@Composable
fun ProvideGlassBackdrop(backdrop: GlassBackdrop, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
}

/**
 * How a glass surface looks: its [shape], [tint], [outline] and the [blur] of the content behind it.
 *
 * [backdropColor] is the opaque color the backdrop content sits on, such as the screen background.
 * The blur spreads the content into it; without it, the blurred edges would turn see-through and
 * show the sharp content underneath.
 */
@Immutable
data class GlassStyle(
    val shape: Shape,
    val tint: Color,
    val outline: Color,
    val outlineWidth: Dp,
    val blur: Dp,
    val backdropColor: Color,
    val minSdk: Int,
)

object GlassDefaults {
    /**
     * The `overlay.glass` surface with its outline and `glass.blur`, in [shape], over content on
     * [backdropColor].
     */
    @Composable
    @ReadOnlyComposable
    fun style(shape: Shape, backdropColor: Color = StudioTheme.colors.background): GlassStyle {
        val colors = StudioTheme.colors
        val glass = StudioTheme.effects.glass
        return GlassStyle(
            shape = shape,
            tint = colors.overlay.glass,
            outline = colors.overlay.glassOutline,
            outlineWidth = StudioTheme.dimens.stroke.hairline,
            blur = glass.blur,
            backdropColor = backdropColor,
            minSdk = glass.minSdk,
        )
    }
}

/**
 * Draws a glass surface: the content of [backdrop] behind it blurred under the glass tint, and the
 * outline. Below [GlassStyle.minSdk] (Android 12), or without a backdrop, the tint alone stands in:
 * blurring before RenderEffect costs too much for a floating element.
 */
fun Modifier.studioGlass(backdrop: GlassBackdrop?, style: GlassStyle): Modifier {
    val surface = if (backdrop != null && Build.VERSION.SDK_INT >= style.minSdk) {
        Modifier.hazeBlur(
            input = HazeInput.Sources(backdrop.hazeState),
            style = HazeBlurStyle {
                blurRadius(style.blur)
                backgroundColor(style.backdropColor)
                noiseFactor(0f)
                colorEffects(listOf(HazeColorEffect.tint(style.tint)))
                fallbackColorEffect(HazeColorEffect.tint(style.tint))
            },
        )
    } else {
        Modifier.background(style.tint)
    }
    return this
        .clip(style.shape)
        .then(surface)
        .border(style.outlineWidth, style.outline, style.shape)
}
