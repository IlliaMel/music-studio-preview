package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.track.Track

/**
 * What the player is doing. Positions and durations are in milliseconds, like the media APIs that
 * report them many times a second.
 *
 * @param queue the tracks in the order they play: shuffled while [shuffle] is on.
 * @param currentIndex the playing track's place in [queue].
 * @param stationName the station the queue is, `null` for a plain queue. A station plays round and
 *   round, like a radio, so it can be stopped but not paused or sought, and [repeat] doesn't apply.
 */
data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val stationName: String? = null,
) {
    /** The track that is playing or paused, `null` when nothing is loaded. */
    val current: Track? get() = queue.getOrNull(currentIndex)

    val durationMs: Long get() = current?.duration?.inWholeMilliseconds ?: 0

    val isStation: Boolean get() = stationName != null

    /** Whether the queue starts over after its last track: it repeats, or it is a station. */
    val loops: Boolean get() = repeat == RepeatMode.All || isStation

    /** Whether there is a track to skip to. */
    val hasNext: Boolean get() = current != null && (currentIndex < queue.lastIndex || loops)
}

enum class RepeatMode {
    Off,

    /** Start the queue again after its last track. */
    All,

    /** Play the current track again and again. */
    One,
}
