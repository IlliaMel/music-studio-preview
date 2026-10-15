package com.illiamelnyk.studio.feature.listen.domain

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.Track
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A mix, chart or playlist of the demo catalog. */
data class CatalogCollection(
    val id: String,
    val title: String,
    val coverSeed: CoverSeed,
    val tracks: List<Track>,
    val isStation: Boolean = false,
    val kind: CatalogSectionKind? = null,
    val trendSnapshotId: String? = null,
) {
    val totalDuration: Duration get() = tracks.fold(Duration.ZERO) { total, track -> total + track.duration }
}

enum class CatalogSectionKind { Stations, New, Top, Rising, Genres, MadeOnStudio, Remixes, Featured }

data class CatalogSection(val kind: CatalogSectionKind, val collections: List<CatalogCollection>)

/** The demo catalog of Listen. */
interface CatalogRepository {
    suspend fun refresh() = Unit

    /** The collections in the order Listen shows them. */
    fun observeCollections(): Flow<List<CatalogCollection>>

    fun observeSection(kind: CatalogSectionKind): Flow<CatalogSection> =
        observeCollections().map { CatalogSection(kind, it) }

    /** One collection, or `null` when there is none with [id]. */
    fun observeCollection(id: String): Flow<CatalogCollection?>
}
