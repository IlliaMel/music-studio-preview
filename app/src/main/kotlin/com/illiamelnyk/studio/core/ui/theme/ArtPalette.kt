package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors of the artwork the app generates: cover gradients and the orb, from ui-kit.html. They are
 * content, like a photo, not UI roles: they don't follow the light or dark scheme and are never used
 * for text or controls (text on art uses the `overlay.onMedia*` roles).
 */
internal object ArtPalette {
    /** Cover gradients `.g1` to `.g5` of the kit. */
    val coverGradients: List<CoverGradient> = listOf(
        CoverGradient(135f, Color(0xFF7C5CFF), Color(0xFFFF5FA2), 0.55f, Color(0xFFFFB36B)),
        CoverGradient(160f, Color(0xFF0EA5E9), Color(0xFF6366F1), 0.6f, Color(0xFF1E1B4B)),
        CoverGradient(140f, Color(0xFFF97316), Color(0xFFEF4444), 0.5f, Color(0xFF7F1D1D)),
        CoverGradient(150f, Color(0xFF10B981), Color(0xFF0F766E), 0.55f, Color(0xFF0B3B3A)),
        CoverGradient(135f, Color(0xFFF472B6), Color(0xFFA855F7), 0.6f, Color(0xFF312E81)),
    )

    /** The orb (`.s-orb`): a blue sphere with a light highlight and two depth spots. */
    val orbBase = Color(0xFF1662E0)
    val orbHighlight = Color(0xFFEAF6FF)
    val orbDeep = Color(0xFF0B3FD1)
    val orbLight = Color(0xFF2FA4FF)
    val orbGlow = Color(red = 24, green = 142, blue = 236).copy(alpha = 0.45f)
}

/**
 * A three-stop linear gradient in CSS terms: [angle] in degrees clockwise from "to top", and the
 * middle color at [middleStop] of the gradient line.
 */
@Immutable
internal data class CoverGradient(
    val angle: Float,
    val start: Color,
    val middle: Color,
    val middleStop: Float,
    val end: Color,
)
