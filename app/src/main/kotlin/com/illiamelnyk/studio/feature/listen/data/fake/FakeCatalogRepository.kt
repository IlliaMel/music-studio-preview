package com.illiamelnyk.studio.feature.listen.data.fake

import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Collections cut from [FakeCatalog].
 *
 * Temporary: replaced by the backend-backed implementation (`GET /v1/catalog`).
 */
class FakeCatalogRepository : CatalogRepository {

    private val collections = FakeCatalog.tracks.let { tracks ->
        listOf(
            CatalogCollection("trending", "Trending Now", CoverSeed("Trending Now"), tracks.take(6)),
            CatalogCollection("new", "New This Week", CoverSeed("New This Week"), tracks.drop(6)),
            CatalogCollection("vocals", "Sing Along", CoverSeed("Sing Along"), tracks.filterNot { it.instrumental }),
            CatalogCollection("focus", "Deep Focus", CoverSeed("Deep Focus"), tracks.filter { it.instrumental }),
        )
    }

    override fun observeCollections(): Flow<List<CatalogCollection>> = flowOf(collections)

    override fun observeCollection(id: String): Flow<CatalogCollection?> = flowOf(collections.find { it.id == id })
}
