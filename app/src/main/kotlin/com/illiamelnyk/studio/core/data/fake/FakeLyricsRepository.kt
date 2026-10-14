package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.track.TrackId
import kotlinx.coroutines.delay

/**
 * Lyrics from [FakeCatalog].
 *
 * Temporary: replaced by the backend-backed implementation.
 */
class FakeLyricsRepository : LyricsRepository {
    override suspend fun lyrics(id: TrackId): Outcome<List<LyricLine>> {
        delay(FakeNetwork.latency)
        return Outcome.Success(FakeCatalog.lyrics[id].orEmpty())
    }
}
