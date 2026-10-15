package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.Track

/** Moves a track immediately after the current one without changing its clock or pause state. */
class PlayNext(private val playback: PlaybackController) {
    operator fun invoke(track: Track) = playback.enqueueNext(track)
}
