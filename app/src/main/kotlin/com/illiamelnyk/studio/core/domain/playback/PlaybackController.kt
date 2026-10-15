package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.track.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * The one player of the app. It outlives screens, so commands return at once, like a media
 * controller's, and their effect shows up in [state].
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Replaces the queue with [queue] and plays it from [startIndex]. */
    fun play(queue: List<Track>, startIndex: Int = 0)

    /**
     * Replaces the queue with [queue] and plays it as the station [name]: from its first track,
     * round and round until [stop]. See [PlaybackState.stationName].
     */
    fun playStation(name: String, queue: List<Track>)

    /** Moves [track] immediately after the current one without changing its clock or play state. */
    fun enqueueNext(track: Track)

    /** Pauses if playing, plays if paused. */
    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    /** Skips to the next track. On the last one it does nothing, unless the queue repeats or is a station. */
    fun next()

    /** Restarts the current track a few seconds in, otherwise goes back to the previous one. */
    fun previous()

    fun setShuffle(enabled: Boolean)

    fun setRepeat(mode: RepeatMode)

    /** Keeps the current track playing and drops the rest of the queue. */
    fun clearUpNext()

    /** Stops and clears the queue: nothing is loaded afterwards. */
    fun stop()
}
