package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.track.Track

/**
 * What the player is doing. Positions and durations are in milliseconds, like the media APIs that
 * report them many times a second.
 *
 * @param queue the tracks in the order they play: shuffled while [shuffle] is on.
 * @param currentIndex the playing track's place in [queue].
 * @param isStation the queue is a station: it plays round and round, like a radio, so it can be
 *   stopped but not paused or seeked, and [repeat] doesn't apply to it.
 */
data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val isStation: Boolean = false,
) {
    /** The track that is playing or paused, `null` when nothing is loaded. */
    val current: Track? get() = queue.getOrNull(currentIndex)

    val durationMs: Long get() = current?.duration?.inWholeMilliseconds ?: 0
}

enum class RepeatMode {
    Off,

    /** Start the queue again after its last track. */
    All,

    /** Play the current track again and again. */
    One,
}
