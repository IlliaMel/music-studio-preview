package com.illiamelnyk.studio.feature.listen.data

import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.feature.listen.data.fake.FakeCatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogSection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CachedCatalogRepository(private val data: DurableStudioRepository) : CatalogRepository {
    private val bundled = FakeCatalogRepository()
    override fun observeCollections(): Flow<List<CatalogCollection>> =
        combine(bundled.observeCollections(), data.session) {
                collections,
                _,
            ->
            collections.map(::resolve)
        }
    override fun observeCollection(id: String): Flow<CatalogCollection?> =
        combine(bundled.observeCollection(id), data.session) { collection, _ ->
            collection?.let(::resolve)
        }
    override fun observeSection(kind: CatalogSectionKind): Flow<CatalogSection> =
        combine(bundled.observeSection(kind), data.session) { section, _ ->
            section.copy(collections = section.collections.map(::resolve))
        }
    private fun resolve(collection: CatalogCollection): CatalogCollection =
        collection.copy(tracks = collection.tracks.map(data::catalogTrack))
}
