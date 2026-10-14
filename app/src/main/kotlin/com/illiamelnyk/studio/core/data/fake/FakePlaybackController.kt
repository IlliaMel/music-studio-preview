package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Track
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A player without sound: while playing, the position moves forward every [TICK], and at the end
 * of a track the next one starts, following shuffle and repeat like a real player.
 *
 * Commands come from the main thread; the clock runs in [scope], which the controller owns like a
 * playback service owns its player. Tests pass a test scope and a seeded [random].
 *
 * Temporary: replaced by a Media3 implementation.
 */
class FakePlaybackController(private val scope: CoroutineScope, private val random: Random = Random.Default) :
    PlaybackController {

    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    /** The queue as given to [play]; [PlaybackState.queue] is this in play order. */
    private var originalQueue: List<Track> = emptyList()

    /** For each place in the play order, the track's index in [originalQueue]. */
    private var playOrder: List<Int> = emptyList()
    private var clock: Job? = null

    override fun play(queue: List<Track>, startIndex: Int) {
        require(startIndex in queue.indices) { "Start index $startIndex is outside the queue of ${queue.size}" }
        originalQueue = queue
        val shuffle = mutableState.value.shuffle
        playOrder = if (shuffle) shuffledOrder(first = startIndex) else queue.indices.toList()
        mutableState.update {
            it.copy(
                queue = playOrder.map(queue::get),
                currentIndex = if (shuffle) 0 else startIndex,
                positionMs = 0,
                isPlaying = true,
            )
        }
        startClock()
    }

    override fun togglePlayPause() {
        val state = mutableState.value
        if (state.current == null) return
        if (state.isPlaying) {
            stopClock()
            mutableState.update { it.copy(isPlaying = false) }
        } else {
            // Playing again after the queue ended starts the last track over.
            val atEnd = state.positionMs >= state.durationMs
            mutableState.update { it.copy(isPlaying = true, positionMs = if (atEnd) 0 else it.positionMs) }
            startClock()
        }
    }

    override fun seekTo(positionMs: Long) {
        mutableState.update { it.copy(positionMs = positionMs.coerceIn(0, it.durationMs)) }
    }

    override fun next() {
        mutableState.update { state ->
            when {
                state.currentIndex < state.queue.lastIndex -> state.atTrack(state.currentIndex + 1)
                state.repeat == RepeatMode.All && state.queue.isNotEmpty() -> state.atTrack(0)
                else -> state
            }
        }
    }

    override fun previous() {
        mutableState.update { state ->
            when {
                state.positionMs > RESTART_THRESHOLD.inWholeMilliseconds -> state.copy(positionMs = 0)
                state.currentIndex > 0 -> state.atTrack(state.currentIndex - 1)
                state.repeat == RepeatMode.All && state.queue.isNotEmpty() -> state.atTrack(state.queue.lastIndex)
                else -> state.copy(positionMs = 0)
            }
        }
    }

    override fun setShuffle(enabled: Boolean) {
        val state = mutableState.value
        if (state.shuffle == enabled) return
        if (state.current == null) {
            mutableState.update { it.copy(shuffle = enabled) }
            return
        }
        // The current track keeps playing; only what comes after it changes.
        val originalIndex = playOrder[state.currentIndex]
        playOrder = if (enabled) shuffledOrder(first = originalIndex) else originalQueue.indices.toList()
        mutableState.update {
            it.copy(
                queue = playOrder.map(originalQueue::get),
                currentIndex = if (enabled) 0 else originalIndex,
                shuffle = enabled,
            )
        }
    }

    override fun setRepeat(mode: RepeatMode) {
        mutableState.update { it.copy(repeat = mode) }
    }

    override fun stop() {
        stopClock()
        originalQueue = emptyList()
        playOrder = emptyList()
        mutableState.update { PlaybackState(shuffle = it.shuffle, repeat = it.repeat) }
    }

    private fun startClock() {
        if (clock?.isActive == true) return
        clock = scope.launch {
            while (isActive) {
                delay(TICK)
                mutableState.update { it.advancedBy(TICK.inWholeMilliseconds) }
                if (!mutableState.value.isPlaying) break
            }
        }
    }

    private fun stopClock() {
        clock?.cancel()
        clock = null
    }

    /** [first] and then the rest of the original queue in random order. */
    private fun shuffledOrder(first: Int): List<Int> =
        listOf(first) + originalQueue.indices.filter { it != first }.shuffled(random)

    companion object {
        val TICK = 250.milliseconds

        /** Further into a track than this, "previous" restarts it instead of going back. */
        val RESTART_THRESHOLD = 3.seconds
    }
}

private fun PlaybackState.atTrack(index: Int) = copy(currentIndex = index, positionMs = 0)

/** The state [elapsedMs] later: the position moves on, and at the end of a track the queue does. */
private fun PlaybackState.advancedBy(elapsedMs: Long): PlaybackState {
    if (!isPlaying || current == null) return this
    val position = positionMs + elapsedMs
    return when {
        position < durationMs -> copy(positionMs = position)

        repeat == RepeatMode.One -> copy(positionMs = 0)

        currentIndex < queue.lastIndex -> atTrack(currentIndex + 1)

        repeat == RepeatMode.All -> atTrack(0)

        // The queue has ended: stay on the last track, paused at its end.
        else -> copy(positionMs = durationMs, isPlaying = false)
    }
}
