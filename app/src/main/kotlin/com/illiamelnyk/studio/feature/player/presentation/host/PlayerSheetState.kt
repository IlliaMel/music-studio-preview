package com.illiamelnyk.studio.feature.player.presentation.host

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.illiamelnyk.studio.core.ui.theme.StudioMotion
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whether the full player is open, as one seekable transition from the mini player (`false`) to the
 * full player (`true`). A tap animates it; a drag on either player and the back gesture scrub it, so
 * the full player and its cover follow the finger and settle when it lifts.
 *
 * Every animation of the transition runs linearly over [StudioMotion.Durations.long]; the easing is
 * applied here, to the transition's fraction, so a scrubbed transition tracks the finger and a tapped
 * one still eases.
 */
@Stable
internal class PlayerSheetState(
    expanded: Boolean,
    private val scope: CoroutineScope,
    private val motion: StudioMotion,
) {

    val transitionState = SeekableTransitionState(expanded)

    /** Open or opening. */
    val isExpanded: Boolean get() = transitionState.targetState

    val handlesBack: Boolean get() = transitionState.currentState || transitionState.targetState

    /** How open the full player is, from 0 (the mini player) to 1 (fully open), as it moves. */
    val expandedFraction: Float
        get() = with(transitionState) {
            when {
                currentState == targetState -> if (currentState) 1f else 0f
                targetState -> fraction
                else -> 1f - fraction
            }
        }

    /** How far a drag travels to open or close the player completely: the height of the host, in pixels. */
    var dragDistance: Float = 1f

    /** Drags on the mini player: up opens. */
    val expandDrag = DraggableState { delta -> dragBy(-delta) }

    /** Drags on the full player: down closes. */
    val collapseDrag = DraggableState { delta -> dragBy(delta) }

    private var drag: Drag? = null

    fun expand() = settle(expanded = true)

    fun collapse() = settle(expanded = false)

    /** Starts scrubbing towards [expanded]. Ignored while the player is already moving or is there. */
    fun startDrag(expanded: Boolean) {
        if (drag != null || transitionState.currentState != transitionState.targetState) return
        if (transitionState.currentState == expanded) return
        // Conflated: seeking takes a frame, and only the latest position matters.
        val fractions = Channel<Float>(Channel.CONFLATED)
        val job = scope.launch {
            for (fraction in fractions) transitionState.seekTo(fraction, targetState = expanded)
        }
        drag = Drag(expanded, fractions, job)
    }

    /**
     * Ends the drag: it completes when it went past half way or was flung on at [velocity] (pixels
     * per second towards where it was going); otherwise it goes back.
     */
    fun endDrag(velocity: Float) {
        val ended = drag ?: return
        drag = null
        ended.fractions.close()
        scope.launch {
            ended.job.join()
            val flungOn = velocity / dragDistance > FLING_FRACTION_PER_SECOND
            val flungBack = velocity / dragDistance < -FLING_FRACTION_PER_SECOND
            val complete = flungOn || (ended.fraction > HALF_WAY && !flungBack)
            animateTo(if (complete) ended.target else !ended.target)
        }
    }

    /**
     * Follows the back gesture with a preview of closing, closes when it completes and opens back up
     * when it is cancelled.
     */
    suspend fun collapseWithBack(progress: Flow<BackEventCompat>) {
        val restoreExpanded = transitionState.targetState
        val startExpanded = expandedFraction
        val previewTarget = if (transitionState.currentState == transitionState.targetState) {
            false
        } else {
            transitionState.targetState
        }
        fun fractionFor(expanded: Float) = if (previewTarget) expanded else 1f - expanded
        try {
            transitionState.seekTo(fractionFor(startExpanded), targetState = previewTarget)
            progress.conflate().collect { event ->
                val expanded = startExpanded * (1f - event.progress.coerceIn(0f, 1f))
                transitionState.seekTo(fractionFor(expanded), targetState = previewTarget)
            }
            animateTo(false, backGesture = true)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { animateTo(restoreExpanded, backGesture = true) }
            throw cancelled
        }
    }

    private fun dragBy(deltaPx: Float) {
        val current = drag ?: return
        current.fraction = (current.fraction + deltaPx / dragDistance).coerceIn(0f, 1f)
        current.fractions.trySend(current.fraction)
    }

    private fun settle(expanded: Boolean) {
        scope.launch { animateTo(expanded) }
    }

    /** Animates to [expanded], from wherever the transition is, in the time the rest of the way takes. */
    private suspend fun animateTo(expanded: Boolean, backGesture: Boolean = false) {
        if (transitionState.currentState == transitionState.targetState && transitionState.currentState != expanded) {
            // Sets the other player up first: composing it takes a frame or more, and the clock of
            // the animation shouldn't run meanwhile, nor the cover start before its end is known.
            transitionState.seekTo(0f, targetState = expanded)
        }
        val remaining = if (expanded) 1f - expandedFraction else expandedFraction
        transitionState.animateTo(expanded, settleSpec(remaining, expanded, backGesture))
    }

    private fun settleSpec(remaining: Float, expanded: Boolean, backGesture: Boolean): FiniteAnimationSpec<Float> {
        val duration = if (backGesture) {
            motion.duration.medium.toFloat()
        } else {
            motion.duration.long * if (expanded) 1f else COLLAPSE_DURATION_FRACTION
        }
        return tween(
            (duration * remaining).roundToInt(),
            easing = if (backGesture) LinearEasing else motion.easing.emphasized,
        )
    }

    private class Drag(val target: Boolean, val fractions: Channel<Float>, val job: Job) {
        var fraction = 0f
    }

    private companion object {
        const val HALF_WAY = 0.5f
        const val COLLAPSE_DURATION_FRACTION = 0.75f

        /** A fling faster than this, in player heights per second, completes or cancels the drag. */
        const val FLING_FRACTION_PER_SECOND = 0.5f
    }
}

@Composable
internal fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    val motion = StudioTheme.motion
    val saver = remember(scope, motion) {
        Saver<PlayerSheetState, Boolean>(save = { it.isExpanded }, restore = { PlayerSheetState(it, scope, motion) })
    }
    return rememberSaveable(saver = saver) { PlayerSheetState(expanded = false, scope, motion) }
}
