package com.illiamelnyk.studio.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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
 * How screens replace each other, from the motion tokens: a screen opened on top slides in from
 * the end and back out to it; switching tabs cross-fades, since tabs sit side by side, not on top
 * of each other.
 */
@Stable
class ShellTransitions(motion: StudioMotion, layoutDirection: LayoutDirection) {
    // Positive offsets come from the right; in right-to-left layouts screens come from the left.
    private val fromEnd = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    private val slide = tween<IntOffset>(motion.duration.long, easing = motion.easing.emphasized)
    private val fade = tween<Float>(motion.duration.medium, easing = motion.easing.standard)

    /** Opening a screen on top. */
    val push: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        (slideInHorizontally(slide) { fromEnd * it / ENTER_FRACTION } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { -fromEnd * it / BEHIND_FRACTION } + fadeOut(fade))
    }

    /** Going back from a screen on top. */
    val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        (slideInHorizontally(slide) { -fromEnd * it / BEHIND_FRACTION } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { fromEnd * it / ENTER_FRACTION } + fadeOut(fade))
    }

    /** Metadata of a tab's first screen: arriving at it or leaving it is a cross-fade. */
    val tabRoot: Map<String, Any> = NavDisplay.transitionSpec { fadeIn(fade) togetherWith fadeOut(fade) } +
        NavDisplay.popTransitionSpec { fadeIn(fade) togetherWith fadeOut(fade) }

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
