package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
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
 * of a track the next one starts, following shuffle and repeat like a real player. A station starts
 * over after its last track.
 *
 * Commands come from the main thread; the clock runs in [scope], which the controller owns like a
 * playback service owns its player. Tests pass a test scope and a seeded [random].
 *
 * Temporary: replaced by a Media3 implementation.
 */
class FakePlaybackController(private val scope: CoroutineScope, private val random: Random = Random.Default) :
    PlaybackController {

    // Queue indexes and the clock advance together even when the injected scope runs off main.
    private val playbackLock = Any()
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    /** The queue as given to [play]; [PlaybackState.queue] is this in play order. */
    private var originalQueue: List<Track> = emptyList()

    /** For each place in the play order, the track's index in [originalQueue]. */
    private var playOrder: List<Int> = emptyList()
    private var clock: Job? = null

    override fun play(queue: List<Track>, startIndex: Int) = synchronized(playbackLock) {
        load(queue, startIndex, stationName = null)
    }

    override fun playStation(name: String, queue: List<Track>) = synchronized(playbackLock) {
        load(queue, startIndex = 0, stationName = name)
    }

    override fun enqueueNext(track: Track) {
        synchronized(playbackLock) {
            val state = mutableState.value
            val current = state.current
            if (current == null) {
                play(listOf(track))
                return
            }
            if (current.id == track.id) return
            val currentOriginal = playOrder[state.currentIndex]
            val retained = originalQueue.indices.filter { originalQueue[it].id != track.id }
            val insertAt = retained.indexOf(currentOriginal) + 1
            val updated = retained.map(originalQueue::get).toMutableList().apply { add(insertAt, track) }
            val remapped = retained.mapIndexed { index, old ->
                old to if (index >=
                    insertAt
                ) {
                    index + 1
                } else {
                    index
                }
            }.toMap()
            val order = playOrder.mapNotNull(remapped::get).toMutableList()
            val currentIndex = order.indexOf(remapped.getValue(currentOriginal))
            order.add(currentIndex + 1, insertAt)
            originalQueue = updated
            playOrder = order
            mutableState.update { it.copy(queue = order.map(updated::get), currentIndex = currentIndex) }
        }
    }

    override fun togglePlayPause() {
        synchronized(playbackLock) {
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
    }

    override fun seekTo(positionMs: Long) {
        synchronized(playbackLock) {
            mutableState.update { it.copy(positionMs = positionMs.coerceIn(0, it.durationMs)) }
        }
    }

    override fun next() {
        synchronized(playbackLock) {
            mutableState.update { state ->
                when {
                    state.currentIndex < state.queue.lastIndex -> state.atTrack(state.currentIndex + 1)
                    state.loops && state.queue.isNotEmpty() -> state.atTrack(0)
                    else -> state
                }
            }
        }
    }

    override fun previous() {
        synchronized(playbackLock) {
            mutableState.update { state ->
                when {
                    state.positionMs > RESTART_THRESHOLD.inWholeMilliseconds -> state.copy(positionMs = 0)
                    state.currentIndex > 0 -> state.atTrack(state.currentIndex - 1)
                    state.loops && state.queue.isNotEmpty() -> state.atTrack(state.queue.lastIndex)
                    else -> state.copy(positionMs = 0)
                }
            }
        }
    }

    override fun setShuffle(enabled: Boolean) {
        synchronized(playbackLock) {
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
    }

    override fun setRepeat(mode: RepeatMode) {
        synchronized(playbackLock) {
            mutableState.update { it.copy(repeat = mode) }
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        synchronized(playbackLock) {
            val state = mutableState.value
            if (fromIndex !in state.queue.indices || toIndex !in state.queue.indices || fromIndex == toIndex) return
            val currentOriginal = playOrder.getOrNull(state.currentIndex) ?: return
            val order = playOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            val currentIndex = order.indexOf(currentOriginal)
            if (state.shuffle) {
                playOrder = order
            } else {
                originalQueue = order.map(originalQueue::get)
                playOrder = originalQueue.indices.toList()
            }
            mutableState.update {
                it.copy(queue = playOrder.map(originalQueue::get), currentIndex = currentIndex)
            }
        }
    }

    override fun removeTrack(id: TrackId) {
        synchronized(playbackLock) {
            val state = mutableState.value
            val current = state.current ?: return
            if (originalQueue.none { it.id == id }) return
            val retained = originalQueue.indices.filter { originalQueue[it].id != id }
            if (retained.isEmpty()) {
                stop()
                return
            }
            val remap = retained.mapIndexed { index, old -> old to index }.toMap()
            val currentOriginal = playOrder[state.currentIndex]
            val order = playOrder.mapNotNull(remap::get)
            val currentRemoved = current.id == id
            val nextIndex = if (currentRemoved) {
                state.currentIndex.coerceAtMost(order.lastIndex)
            } else {
                order.indexOf(remap.getValue(currentOriginal))
            }
            originalQueue = retained.map(originalQueue::get)
            playOrder = order
            mutableState.value = state.copy(
                queue = order.map(originalQueue::get),
                currentIndex = nextIndex,
                positionMs = if (currentRemoved) 0 else state.positionMs,
            )
        }
    }

    override fun clearUpNext() {
        synchronized(playbackLock) {
            val current = mutableState.value.current ?: return
            originalQueue = listOf(current)
            playOrder = listOf(0)
            mutableState.update { it.copy(queue = originalQueue, currentIndex = 0) }
        }
    }

    override fun stop() {
        synchronized(playbackLock) {
            stopClock()
            originalQueue = emptyList()
            playOrder = emptyList()
            mutableState.update { PlaybackState(shuffle = it.shuffle, repeat = it.repeat) }
        }
    }

    private fun load(queue: List<Track>, startIndex: Int, stationName: String?) {
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
                stationName = stationName,
            )
        }
        startClock()
    }

    private fun startClock() {
        if (clock?.isActive == true) return
        clock = scope.launch {
            while (isActive) {
                delay(TICK)
                val playing = synchronized(playbackLock) {
                    if (!isActive) return@synchronized false
                    mutableState.update { it.advancedBy(TICK.inWholeMilliseconds) }
                    mutableState.value.isPlaying.also { playing -> if (!playing) clock = null }
                }
                if (!playing) break
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

        repeat == RepeatMode.One && !isStation -> copy(positionMs = 0)

        currentIndex < queue.lastIndex -> atTrack(currentIndex + 1)

        loops -> atTrack(0)

        // The queue has ended: stay on the last track, paused at its end.
        else -> copy(positionMs = durationMs, isPlaying = false)
    }
}
