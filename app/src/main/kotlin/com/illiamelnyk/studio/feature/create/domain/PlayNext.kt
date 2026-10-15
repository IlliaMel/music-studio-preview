package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.Track

/**
 * Puts [Track] right after the one that plays, or plays it when nothing is loaded. A track already
 * in the queue moves instead of playing twice.
 *
 * [PlaybackController] can't insert into its queue yet, so the queue is replaced around the current
 * track, which keeps its place, its position and whether it plays. With shuffle on, the controller
 * reshuffles what comes after the current track, so the track joins the rest instead of coming
 * next; a controller with an insert (Media3's `addMediaItem`) removes both workarounds.
 */
class PlayNext(private val playback: PlaybackController) {

    operator fun invoke(track: Track) {
        val state = playback.state.value
        val current = state.current
        if (current == null) {
            playback.play(listOf(track))
            return
        }
        if (current.id == track.id) return
        val before = state.queue.subList(0, state.currentIndex).filterNot { it.id == track.id }
        val after = state.queue.subList(state.currentIndex + 1, state.queue.size).filterNot { it.id == track.id }
        playback.play(queue = before + current + track + after, startIndex = before.size)
        playback.seekTo(state.positionMs)
        if (!state.isPlaying) playback.togglePlayPause()
    }
}
