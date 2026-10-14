package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Shadows, glows and glass from `elevation`, `glow` and `glass` in tokens.json. They are the same in
 * both schemes, like the colors on cover art.
 */
@Immutable
data class StudioEffects(val elevation: Elevation = Elevation(), val glow: Glow = Glow(), val glass: Glass = Glass()) {
    @Immutable
    data class Elevation(
        /** Toasts and other floating elements. */
        val floating: DropShadow = DropShadow(offsetY = 12.dp, blur = 32.dp, color = rgba(0, 0, 0, 0.4f)),
        /** Menus and popovers. */
        val menu: DropShadow = DropShadow(offsetY = 16.dp, blur = 40.dp, color = rgba(0, 0, 0, 0.5f)),
        /** The cover in a collection header and in the player. */
        val artwork: DropShadow = DropShadow(offsetY = 12.dp, blur = 24.dp, color = rgba(0, 0, 0, 0.45f)),
    )

    @Immutable
    data class Glow(
        /** The orb's glow, which breathes between the two ends once per `motion.loop.glowBreath`. */
        val orb: Breathing = Breathing(
            blurMin = 24.dp,
            blurMax = 40.dp,
            alphaMin = 0.3f,
            alphaMax = 0.55f,
            color = Color(0xFF188EEC),
        ),
    )

    /** Frosted glass: the content behind is blurred by [blur], from Android [minSdk] on. */
    @Immutable
    data class Glass(val blur: Dp = 16.dp, val minSdk: Int = 31)

    /** A CSS `box-shadow: 0 offsetY blur color`, drawn with `Modifier.dropShadow`. */
    @Immutable
    data class DropShadow(val offsetY: Dp, val blur: Dp, val color: Color) {
        fun toShadow(): Shadow = Shadow(radius = blur, color = color, offset = DpOffset(0.dp, offsetY))
    }

    /** A glow whose blur and opacity move between a minimum and a maximum. */
    @Immutable
    data class Breathing(val blurMin: Dp, val blurMax: Dp, val alphaMin: Float, val alphaMax: Float, val color: Color)
}
