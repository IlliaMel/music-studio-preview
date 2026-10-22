package com.illiamelnyk.studio.core.data.demo

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.track.TrackId

/** Bundled demonstration lyrics; no network request or simulated network delay. */
internal class DemoLyricsRepository : LyricsRepository {
    override suspend fun lyrics(id: TrackId): Outcome<List<LyricLine>> =
        Outcome.Success(DemoCatalog.lyrics[id].orEmpty())
}
