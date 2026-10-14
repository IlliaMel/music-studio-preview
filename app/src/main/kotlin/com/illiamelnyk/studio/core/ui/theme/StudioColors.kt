package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color roles of the design system, one to one with `color` (dark) and `colorLight` in
 * tokens.json. Components pick a role, never a raw color, so both schemes stay consistent and every
 * text/background pair keeps the contrast tokens.json was checked for.
 */
@Immutable
data class StudioColors(
    /** Screen background. */
    val background: Color,
    val surface: Surface,
    val content: Content,
    val inverse: Inverse,
    val outline: Outline,
    val accent: Accent,
    val error: Error,
    val overlay: Overlay,
) {
    /** Surfaces above the background, each level a step closer to the viewer. */
    @Immutable
    data class Surface(
        /** Sheets, dialogs, menus, the navigation bar. */
        val level1: Color,
        /** Text fields and the composer. */
        val level2: Color,
        /** Chips, tiles, pill rows and secondary buttons. */
        val level3: Color,
        /** Selected or pressed elements on a surface, version tags. */
        val level4: Color,
    )

    /** Text and icons on the background and surfaces. */
    @Immutable
    data class Content(
        val primary: Color,
        /** Artist names, metadata, captions. */
        val secondary: Color,
        /** Hints and placeholders. */
        val tertiary: Color,
        /** Disabled elements only: it doesn't meet text contrast, which WCAG doesn't require there. */
        val disabled: Color,
    )

    /** The one light pill on a dark screen (and the other way round): main action, selected segment and chip. */
    @Immutable
    data class Inverse(val surface: Color, val content: Color)

    @Immutable
    data class Outline(
        /** Borders of fields and cards. */
        val subtle: Color,
        /** Border of a focused field. */
        val strong: Color,
    )

    @Immutable
    data class Accent(
        /** The verified artist mark. */
        val verified: Color,
        val onVerified: Color,
        /** The NEW badge. */
        val new: Color,
        val onNew: Color,
        /** The DEMO and MOCK badges. */
        val demo: Color,
        val onDemo: Color,
    )

    @Immutable
    data class Error(
        /** Error text and icons. */
        val content: Color,
        /** Background of a failed row. */
        val surface: Color,
        /** Text on a destructive button, which uses [content] as its background. */
        val onError: Color,
    )

    /** Colors drawn over other content or over cover art; the media colors are the same in both schemes. */
    @Immutable
    data class Overlay(
        /** Dims the screen under a sheet or dialog. */
        val scrim: Color,
        /** Floating glass elements. */
        val glass: Color,
        val glassOutline: Color,
        /** Text and icons on glass. */
        val onGlass: Color,
        /** Text and icons on cover art or its color. */
        val onMediaPrimary: Color,
        val onMediaSecondary: Color,
        /** The track of a slider on cover color. */
        val onMediaTrack: Color,
    )
}

/** The main scheme, `color` in tokens.json. */
fun darkStudioColors(): StudioColors = StudioColors(
    background = Color(0xFF111111),
    surface = StudioColors.Surface(
        level1 = Color(0xFF161616),
        level2 = Color(0xFF1A1A1A),
        level3 = Color(0xFF1E1E1E),
        level4 = Color(0xFF2E2E2E),
    ),
    content = StudioColors.Content(
        primary = Color(0xFFEBEBEB),
        secondary = Color(0xFFA0A0A0),
        tertiary = Color(0xFF8A8A8A),
        disabled = Color(0xFF5D5D5D),
    ),
    inverse = StudioColors.Inverse(surface = Color(0xFFEBEBEB), content = Color(0xFF111111)),
    outline = StudioColors.Outline(subtle = Color(0xFF262626), strong = Color(0xFF3A3A3A)),
    accent = StudioColors.Accent(
        verified = Color(0xFF188EEC),
        onVerified = Color(0xFFFFFFFF),
        new = Color(0xFFD87644),
        onNew = Color(0xFF111111),
        demo = Color(0xFF3B2B17),
        onDemo = Color(0xFFF0B27A),
    ),
    error = StudioColors.Error(
        content = Color(0xFFFE5054),
        surface = Color(0xFF2A1717),
        onError = Color(0xFF111111),
    ),
    overlay = StudioColors.Overlay(
        scrim = rgba(0, 0, 0, 0.6f),
        glass = rgba(40, 40, 42, 0.55f),
        glassOutline = rgba(255, 255, 255, 0.08f),
        onGlass = Color(0xFFEBEBEB),
        onMediaPrimary = Color(0xFFFFFFFF),
        onMediaSecondary = rgba(255, 255, 255, 0.7f),
        onMediaTrack = rgba(255, 255, 255, 0.2f),
    ),
)

/** The light scheme, `colorLight` in tokens.json: the same roles. */
fun lightStudioColors(): StudioColors = StudioColors(
    background = Color(0xFFFAFAFA),
    surface = StudioColors.Surface(
        level1 = Color(0xFFFFFFFF),
        level2 = Color(0xFFF2F2F2),
        level3 = Color(0xFFEBEBEB),
        level4 = Color(0xFFDCDCDC),
    ),
    content = StudioColors.Content(
        primary = Color(0xFF111111),
        secondary = Color(0xFF5C5C5C),
        tertiary = Color(0xFF666666),
        disabled = Color(0xFFA8A8A8),
    ),
    inverse = StudioColors.Inverse(surface = Color(0xFF111111), content = Color(0xFFFAFAFA)),
    outline = StudioColors.Outline(subtle = Color(0xFFE3E3E3), strong = Color(0xFFC7C7C7)),
    accent = StudioColors.Accent(
        verified = Color(0xFF1477D1),
        onVerified = Color(0xFFFFFFFF),
        new = Color(0xFFD87644),
        onNew = Color(0xFF111111),
        demo = Color(0xFFFCE8D2),
        onDemo = Color(0xFF8A4B12),
    ),
    error = StudioColors.Error(
        content = Color(0xFFC9282F),
        surface = Color(0xFFFDECEC),
        onError = Color(0xFFFFFFFF),
    ),
    overlay = StudioColors.Overlay(
        scrim = rgba(0, 0, 0, 0.4f),
        glass = rgba(255, 255, 255, 0.6f),
        glassOutline = rgba(0, 0, 0, 0.06f),
        onGlass = Color(0xFF111111),
        onMediaPrimary = Color(0xFFFFFFFF),
        onMediaSecondary = rgba(255, 255, 255, 0.7f),
        onMediaTrack = rgba(255, 255, 255, 0.2f),
    ),
)

/** Translucent colors in the `rgba()` notation of tokens.json, so the values compare at a glance. */
internal fun rgba(red: Int, green: Int, blue: Int, alpha: Float): Color = Color(red, green, blue).copy(alpha = alpha)
