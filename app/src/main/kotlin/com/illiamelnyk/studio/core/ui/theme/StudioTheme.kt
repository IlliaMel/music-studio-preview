package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import com.illiamelnyk.studio.core.ui.foundation.LocalStudioContentColor
import com.illiamelnyk.studio.core.ui.foundation.PressScaleIndication

/**
 * The design system's theme. Everything below it reads colors, type, dimensions, shapes, motion and
 * effects through [StudioTheme]; `clickable` gets the press-scale feedback and a focus ring instead of
 * a ripple, and text defaults to the primary content color.
 *
 * @param darkTheme the dark scheme is the main one; by default the scheme follows the system setting.
 */
@Composable
fun StudioTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = remember(darkTheme) { if (darkTheme) darkStudioColors() else lightStudioColors() }
    val density = LocalDensity.current
    val dimens = remember(density) {
        val textHeight = with(density) {
            Typography.titleSmall.lineHeight.toDp() +
                Typography.bodySmall.lineHeight.toDp()
        }
        Dimens.copy(
            size = Dimens.size.copy(
                component = Dimens.size.component.copy(
                    miniPlayer = maxOf(Dimens.size.component.miniPlayer, textHeight + Dimens.spacing.m),
                ),
            ),
        )
    }
    val selectionColors = remember(colors) {
        TextSelectionColors(handleColor = colors.content.primary, backgroundColor = colors.outline.strong)
    }
    val indication = remember(colors) {
        PressScaleIndication(Motion, colors.outline.strong, Dimens.stroke.thick, Shapes.full)
    }
    CompositionLocalProvider(
        LocalStudioColors provides colors,
        LocalStudioTypography provides Typography,
        LocalStudioDimens provides dimens,
        LocalStudioShapes provides Shapes,
        LocalStudioMotion provides Motion,
        LocalStudioEffects provides Effects,
        LocalIndication provides indication,
        LocalStudioContentColor provides colors.content.primary,
        LocalTextSelectionColors provides selectionColors,
        content = content,
    )
}

/**
 * The current theme values, for example `StudioTheme.colors.content.primary` or
 * `StudioTheme.dimens.layout.screenPadding`.
 */
object StudioTheme {
    val colors: StudioColors
        @Composable @ReadOnlyComposable
        get() = LocalStudioColors.current

    val typography: StudioTypography
        @Composable @ReadOnlyComposable
        get() = LocalStudioTypography.current

    val dimens: StudioDimens
        @Composable @ReadOnlyComposable
        get() = LocalStudioDimens.current

    val shapes: StudioShapes
        @Composable @ReadOnlyComposable
        get() = LocalStudioShapes.current

    val motion: StudioMotion
        @Composable @ReadOnlyComposable
        get() = LocalStudioMotion.current

    val effects: StudioEffects
        @Composable @ReadOnlyComposable
        get() = LocalStudioEffects.current
}

// Static locals: the values change only when the scheme switches, and then everything below has to
// redraw anyway, so tracking individual reads would cost more than it saves. There are no defaults: a
// component outside StudioTheme would quietly draw with made-up values, so the first read fails instead.
internal val LocalStudioColors = staticCompositionLocalOf<StudioColors> { missingTheme() }
internal val LocalStudioTypography = staticCompositionLocalOf<StudioTypography> { missingTheme() }
internal val LocalStudioDimens = staticCompositionLocalOf<StudioDimens> { missingTheme() }
internal val LocalStudioShapes = staticCompositionLocalOf<StudioShapes> { missingTheme() }
internal val LocalStudioMotion = staticCompositionLocalOf<StudioMotion> { missingTheme() }
internal val LocalStudioEffects = staticCompositionLocalOf<StudioEffects> { missingTheme() }

internal fun missingTheme(): Nothing = error("StudioTheme is missing: wrap the content in StudioTheme { }")

// Base tokens; the provided mini-player height also follows the system text scale.
private val Typography = StudioTypography()
private val Dimens = StudioDimens()
private val Shapes = StudioShapes()
private val Motion = StudioMotion()
private val Effects = StudioEffects()
