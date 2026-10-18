package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * How screens replace each other, from the motion tokens. A screen opened on top slides in from the
 * end and fades in over the screen below, which stays opaque and drifts back; going back reverses
 * it. Tabs switch immediately so their content or loading skeleton is visible on the next frame.
 */
@Stable
class ShellTransitions(motion: StudioMotion, layoutDirection: LayoutDirection) {
    // Positive offsets come from the right; in right-to-left layouts screens come from the left.
    private val fromEnd = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    private val slide = tween<IntOffset>(motion.duration.long, easing = motion.easing.emphasized)
    private val backSlide = tween<IntOffset>(motion.duration.long, easing = LinearEasing)
    private val backFade = tween<Float>(motion.duration.long, easing = LinearEasing)
    private val fade = tween<Float>(motion.duration.medium, easing = motion.easing.standard)
    private val fadeOutFirst = tween<Float>(motion.duration.short, easing = motion.easing.standard)
    private val fadeInAfter =
        tween<Float>(motion.duration.medium, delayMillis = motion.duration.short, easing = motion.easing.standard)

    /** Opening a screen on top. */
    val push: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        (slideInHorizontally(slide) { fromEnd * it / ENTER_FRACTION } + fadeIn(fade)) togetherWith
            slideOutHorizontally(slide) { -fromEnd * it / BEHIND_FRACTION }
    }

    /** Going back from a screen on top. */
    val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(backSlide) { -fromEnd * it / BEHIND_FRACTION } togetherWith
            (slideOutHorizontally(backSlide) { fromEnd * it / ENTER_FRACTION } + fadeOut(backFade))
    }

    val tabChange: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        EnterTransition.None togetherWith ExitTransition.None
    }

    /** Switching between the flows of the app. */
    val fadeThrough: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(fadeInAfter) togetherWith fadeOut(fadeOutFirst)
    }

    /** Tab selection replaces its root without waiting for an exit animation. */
    val tabRoot: Map<String, Any> = tabRootWhile { true }

    /**
     * Metadata of a screen that is a tab's first screen only while [isTabRoot] says so, such as
     * Library: a tab in one shell variant, opened on top of Listen in the other. It is asked each
     * time the screen comes or goes, so it follows a change of variant.
     */
    fun tabRootWhile(isTabRoot: () -> Boolean): Map<String, Any> =
        NavDisplay.transitionSpec { if (isTabRoot()) tabChange() else null } +
            NavDisplay.popTransitionSpec { if (isTabRoot()) tabChange() else null }

    private companion object {
        /** The new screen starts a third of the width away. */
        const val ENTER_FRACTION = 3

        /** The screen below moves a little the other way, for depth. */
        const val BEHIND_FRACTION = 6
    }
}

@Composable
fun rememberShellTransitions(): ShellTransitions {
    val motion = StudioTheme.motion
    val layoutDirection = LocalLayoutDirection.current
    return remember(motion, layoutDirection) { ShellTransitions(motion, layoutDirection) }
}
