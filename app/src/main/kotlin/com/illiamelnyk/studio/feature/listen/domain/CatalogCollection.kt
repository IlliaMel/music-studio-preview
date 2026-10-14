package com.illiamelnyk.studio.feature.listen.domain

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.Track
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** A mix, chart or playlist of the demo catalog. */
data class CatalogCollection(val id: String, val title: String, val coverSeed: CoverSeed, val tracks: List<Track>) {
    val totalDuration: Duration get() = tracks.fold(Duration.ZERO) { total, track -> total + track.duration }
}

/** The demo catalog of Listen. */
interface CatalogRepository {
    /** The collections in the order Listen shows them. */
    fun observeCollections(): Flow<List<CatalogCollection>>

    /** One collection, or `null` when there is none with [id]. */
    fun observeCollection(id: String): Flow<CatalogCollection?>
}
