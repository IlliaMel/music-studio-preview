package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.network.CatalogDto
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.Track

/** Owner-validated catalog transport and projection; no library mutation or session commands. */
internal interface StudioCatalogAccess {
    suspend fun fetchCatalog(): Outcome<CatalogDto>
    fun registerCatalogTracks(tracks: List<Track>)
    fun catalogTrack(track: Track): Track
}
