package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId

/** Session-local rename overlay until the repository supports PATCH /v1/tracks/{id}. */
class TrackRenames {
    private val names = mutableMapOf<TrackId, String>()

    fun apply(track: Track): Track = names[track.id]?.let { track.copy(title = it) } ?: track

    fun rename(id: TrackId, title: String) {
        require(title.isNotBlank())
        names[id] = title.trim()
    }

    fun remove(id: TrackId) {
        names.remove(id)
    }
}
