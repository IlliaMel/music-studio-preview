package com.illiamelnyk.studio.feature.player.presentation.host

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerRoute
import com.illiamelnyk.studio.feature.player.presentation.SoonFeature
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerComingSoonSheet
import org.koin.compose.koinInject

/**
 * What the shell's mini player slot needs from the [PlayerHost] around it: the transition between
 * the mini player and the full player, and the scope their shared cover travels in.
 */
@Stable
class PlayerHostScope internal constructor(
    internal val sheet: PlayerSheetState,
    internal val sharedTransitionScope: SharedTransitionScope,
    internal val transition: Transition<Boolean>,
) {
    internal var remixSoonVisible by mutableStateOf(false)

    /** Opens the full player from a track selected in the shell. */
    fun expand() = sheet.expand()
}

/**
 * The full player over [content], the signed-in shell, which places the mini player
 * ([com.illiamelnyk.studio.feature.player.presentation.mini.MiniPlayerHost]) with the [PlayerHostScope]
 * it gets. Tapping the mini player or dragging it up opens the full player; dragging that down, its
 * collapse button or back closes it. The cover travels between the two as a shared element.
 *
 * The full player is an overlay rather than a Navigation 3 destination: the mini player sits in the
 * shell's bottom bars, outside the NavDisplay, so a destination's transition and the mini player's
 * would be two transitions that a drag or the back gesture can't scrub together. Here both players
 * hang off one seekable transition, and the bars under the player stay put instead of re-laying out
 * the screens on every frame of the animation.
 */
// collapseWithBack collects the predictive Back flow and handles cancellation in PlayerSheetState.
@Suppress("NoCollectCallFound")
@Composable
fun PlayerHost(modifier: Modifier = Modifier, content: @Composable (PlayerHostScope) -> Unit) {
    val sheet = rememberPlayerSheetState()
    SharedTransitionLayout(modifier.onSizeChanged { sheet.dragDistance = it.height.toFloat() }) {
        val transition = rememberTransition(sheet.transitionState, label = "player")
        val sharedTransitionScope = this
        val host = remember(sheet, sharedTransitionScope, transition) {
            PlayerHostScope(sheet, sharedTransitionScope, transition)
        }
        val covered by remember(sheet) { derivedStateOf { sheet.expandedFraction == 1f } }
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val shell = measurable.measure(constraints)
                    layout(shell.width, shell.height) {
                        // While the player covers the shell, the shell keeps its state but isn't
                        // placed: nothing of it is drawn, touched or read by TalkBack, and what
                        // animates in it, such as the equalizer of a playing row, redraws nothing.
                        if (!covered) shell.place(0, 0)
                    }
                },
        ) {
            content(host)
        }
        FullPlayer(host)
        MiniPlayerRemix(host)
    }
    PlayerSystemBars(onMedia = { sheet.expandedFraction > STATUS_BAR_COVERED })
    PredictiveBackHandler(enabled = sheet.isExpanded) { progress -> sheet.collapseWithBack(progress) }
}

@Composable
private fun FullPlayer(host: PlayerHostScope) {
    val sheet = host.sheet
    val slide = tween<IntOffset>(StudioTheme.motion.duration.long, easing = LinearEasing)
    host.transition.AnimatedVisibility(
        visible = { expanded -> expanded },
        enter = slideInVertically(slide) { it },
        exit = slideOutVertically(slide) { it },
    ) {
        // The player is on cover color in both schemes, so its glass, sheets and menus are dark too.
        StudioTheme(darkTheme = true) {
            PlayerRoute(
                onCollapse = sheet::collapse,
                contentPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues(),
                coverModifier = Modifier.sharedPlayerCover(host, this@AnimatedVisibility),
                bodyModifier = Modifier.draggable(
                    state = sheet.collapseDrag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { sheet.startDrag(expanded = false) },
                    onDragStopped = { velocity -> sheet.endDrag(velocity) },
                ),
            )
        }
    }
}

/**
 * The shared element of the cover in [visibilityScope], one of the two players. Every animation of
 * the transition is linear; see [PlayerSheetState].
 */
@Composable
internal fun Modifier.sharedPlayerCover(host: PlayerHostScope, visibilityScope: AnimatedVisibilityScope): Modifier {
    val state = host.sharedTransitionScope.rememberSharedContentState(PLAYER_COVER_KEY)
    val duration = StudioTheme.motion.duration.long
    val base = this
    return remember(base, state, visibilityScope, duration) {
        val bounds = BoundsTransform { _, _ -> tween(duration, easing = LinearEasing) }
        with(host.sharedTransitionScope) { base.sharedElement(state, visibilityScope, boundsTransform = bounds) }
    }
}

/**
 * Light system bar icons while [onMedia] says the player covers the bars, whatever the scheme; the
 * scheme's own icons come back after.
 */
@Composable
private fun PlayerSystemBars(onMedia: () -> Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val covered by remember { derivedStateOf(onMedia) }
    // Keyed on the colors too: a change of scheme resets the bars, and this runs again after it.
    DisposableEffect(window, view, covered, StudioTheme.colors) {
        if (!covered) return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        val lightStatusBar = bars.isAppearanceLightStatusBars
        val lightNavigationBar = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = lightStatusBar
            bars.isAppearanceLightNavigationBars = lightNavigationBar
        }
    }
}

private const val PLAYER_COVER_KEY = "player-cover"

/** The player covers the status bar for about the last few percent of its way up. */
private const val STATUS_BAR_COVERED = 0.95f

@Composable
private fun MiniPlayerRemix(host: PlayerHostScope) {
    val feedback: ToastController = koinInject()
    PlayerComingSoonSheet(
        feature = SoonFeature.Remix.takeIf { host.remixSoonVisible },
        onDismissRequest = { host.remixSoonVisible = false },
        onWantIt = {
            host.remixSoonVisible = false
            feedback.show(
                UiText.Resource(R.string.player_want_it_title),
                UiText.Resource(R.string.player_want_it_message),
            )
        },
    )
}
