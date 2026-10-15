package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackStatus

fun interface AutoplayCatalog {
    suspend fun tracks(): List<Track>
}

object AutoplayPolicy {
    fun next(catalog: List<Track>, state: PlaybackState): Track? {
        if (!state.autoplay || state.isStation || state.current == null || state.repeat != RepeatMode.Off) return null
        val excluded = state.autoplayHistory + state.queue.map { it.id }
        return catalog.firstOrNull {
            it.id !in excluded && it.status == TrackStatus.Ready &&
                it.duration.inWholeMilliseconds > 0
        }
    }
}
