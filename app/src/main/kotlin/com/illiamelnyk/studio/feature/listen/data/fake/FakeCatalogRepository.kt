package com.illiamelnyk.studio.feature.listen.data.fake

import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogSection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Collections cut from [FakeCatalog].
 *
 * Temporary: replaced by the backend-backed implementation (`GET /v1/catalog`).
 */
class FakeCatalogRepository : CatalogRepository {

    private val sections = listOf(
        section(CatalogSectionKind.Stations, "Late Night Lo-fi", "Ambient Spaces", "Piano Room"),
        section(CatalogSectionKind.New, "New This Week", "Fresh Starts", "After Hours"),
        section(CatalogSectionKind.Top, "Top 20", "Electronic Mix", "Acoustic Favorites"),
        section(CatalogSectionKind.Rising, "Rising", "Tomorrow's Sound", "New Voices"),
        section(CatalogSectionKind.Genres, "Deep Focus", "Jazz", "Electronic", "Ambient", "Pop", "Rock"),
        section(CatalogSectionKind.MadeOnStudio, "Made on Studio", "Sing Along", "Night Drive"),
        section(CatalogSectionKind.Remixes, "Fresh Remixes", "Reimagined", "Second Take"),
        section(CatalogSectionKind.Featured, "Glass Harbor", "Quiet Horizons", "Warm Light"),
    )

    private val collections = sections.flatMap { it.collections }

    override fun observeCollections(): Flow<List<CatalogCollection>> = flowOf(collections)

    override fun observeCollection(id: String): Flow<CatalogCollection?> = flowOf(
        collections.find { it.id == id } ?: when (id) {
            "trending" -> sections[2].collections.first()
            "new" -> sections[1].collections.first()
            "vocals" -> sections[5].collections[1]
            "focus" -> sections[4].collections.first()
            else -> null
        },
    )

    override fun observeSection(kind: CatalogSectionKind): Flow<CatalogSection> =
        flowOf(sections.first { it.kind == kind })

    private fun section(kind: CatalogSectionKind, vararg names: String): CatalogSection = CatalogSection(
        kind,
        names.mapIndexed { index, name ->
            val catalog = FakeCatalog.tracks
            val offset = (kind.ordinal + index) % catalog.size
            val tracks = (catalog.drop(offset) + catalog.take(offset)).take(12)
            CatalogCollection(
                id = "${kind.name.lowercase()}-$index",
                title = name,
                coverSeed = CoverSeed(name),
                tracks = tracks,
                isStation = kind == CatalogSectionKind.Stations,
            )
        },
    )
}
