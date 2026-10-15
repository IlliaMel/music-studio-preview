package com.illiamelnyk.studio.feature.player.presentation.host

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerRoute

@Stable
class PlayerHostScope internal constructor(
    internal val sheet: PlayerSheetState,
    internal val transition: Transition<Boolean>,
) {
    internal var hostBounds by mutableStateOf(Rect.Zero)
    internal var miniBounds by mutableStateOf<Rect?>(null)

    fun expand() = sheet.expand()
}

@Suppress("NoCollectCallFound")
@Composable
fun PlayerHost(modifier: Modifier = Modifier, content: @Composable (PlayerHostScope) -> Unit) {
    val sheet = rememberPlayerSheetState()
    val transition = rememberTransition(sheet.transitionState, label = "player")
    val host = remember(sheet, transition) { PlayerHostScope(sheet, transition) }
    val covered by remember(sheet) {
        derivedStateOf { sheet.transitionState.currentState && sheet.transitionState.targetState }
    }
    Box(
        modifier.fillMaxSize().onGloballyPositioned {
            host.hostBounds = it.boundsInRoot()
            sheet.dragDistance = it.size.height.toFloat()
        },
    ) {
        Box(
            Modifier.fillMaxSize().layout { measurable, constraints ->
                val shell = measurable.measure(constraints)
                layout(shell.width, shell.height) { if (!covered) shell.place(0, 0) }
            },
        ) { content(host) }
        FullPlayer(host)
    }
    PlayerSystemBars(onMedia = { sheet.expandedFraction > STATUS_BAR_COVERED })
    PredictiveBackHandler(enabled = sheet.isExpanded) { progress -> sheet.collapseWithBack(progress) }
}

@Composable
private fun FullPlayer(host: PlayerHostScope) {
    PlayerCardMotion(host) { modifier ->
        StudioTheme(darkTheme = true) {
            PlayerRoute(
                onCollapse = host.sheet::collapse,
                modifier = modifier,
                contentPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues(),
            )
        }
    }
}

@Composable
internal fun PlayerCardMotion(host: PlayerHostScope, content: @Composable (Modifier) -> Unit) {
    val sheet = host.sheet
    val duration = StudioTheme.motion.duration.long
    val corner = StudioTheme.shapes.xl
    host.transition.AnimatedVisibility(
        visible = { it },
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) {
        val expansion = transition.animateFloat(
            transitionSpec = { tween(duration, easing = LinearEasing) },
            label = "playerCardExpansion",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        content(
            Modifier.fillMaxSize()
                .draggable(
                    state = sheet.collapseDrag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { sheet.startDrag(expanded = false) },
                    onDragStopped = { velocity -> sheet.endDrag(velocity) },
                )
                .graphicsLayer {
                    val progress = expansion.value
                    val mini = host.miniBounds
                    val bounds = host.hostBounds
                    val originX = mini?.let { it.left - bounds.left } ?: 0f
                    val originY = mini?.let { it.top - bounds.top } ?: size.height
                    val startWidth = mini?.width ?: size.width
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = originX * (1f - progress)
                    translationY = originY * (1f - progress)
                    scaleX = lerp(startWidth / size.width.coerceAtLeast(1f), 1f, progress)
                    scaleY = scaleX
                    alpha = (progress / CARD_FADE_FRACTION).coerceIn(0f, 1f)
                    shape = corner
                    clip = progress < 1f
                },
        )
    }
}

@Composable
private fun PlayerSystemBars(onMedia: () -> Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val covered by remember { derivedStateOf(onMedia) }
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

private const val STATUS_BAR_COVERED = 0.95f
private const val CARD_FADE_FRACTION = 0.35f
