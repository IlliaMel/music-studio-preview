package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/** Durations, easings and the press scale from `motion` in tokens.json. Durations are in milliseconds. */
@Immutable
data class StudioMotion(
    val duration: Durations = Durations(),
    val easing: Easings = Easings(),
    /** How far a pressed element shrinks: the press feedback of the design system, instead of a ripple. */
    val pressScale: Float = 0.96f,
    val loop: Loops = Loops(),
) {
    @Immutable
    data class Durations(
        /** Press feedback, color changes. */
        val short: Int = 120,
        /** Moving a selection, showing and hiding small elements. */
        val medium: Int = 250,
        /** Larger elements entering or leaving. */
        val long: Int = 400,
    )

    @Immutable
    data class Easings(
        val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        val emphasized: Easing = CubicBezierEasing(0.3f, 0f, 0f, 1f),
    )

    /**
     * Periods of the looping animations, from the animations in ui-kit.html. They aren't in
     * tokens.json yet, so they are kept here rather than scattered over the components.
     */
    @Immutable
    data class Loops(
        val spinner: Int = 800,
        val equalizer: Int = 1_000,
        val skeleton: Int = 1_400,
        val progress: Int = 1_600,
        val livingCover: Int = 4_000,
        val orb: Int = 7_000,
        /** CSS `ease-in-out`, which the kit's back-and-forth loops use. */
        val easing: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
    )
}
