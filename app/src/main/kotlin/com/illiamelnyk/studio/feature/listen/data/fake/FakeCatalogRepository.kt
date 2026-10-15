package com.illiamelnyk.studio.feature.listen.data.fake

import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogSection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeCatalogRepository : CatalogRepository {
    private val collections = PUBLIC_CATALOG_COLLECTIONS
    override fun observeCollections(): Flow<List<CatalogCollection>> = flowOf(collections)
    override fun observeCollection(id: String): Flow<CatalogCollection?> = flowOf(
        collections.firstOrNull { it.id == id } ?: collections.firstOrNull {
            it.id == when (id) {
                "trending" -> "top-0"
                "new" -> "new-0"
                "vocals" -> "madeonstudio-1"
                "focus" -> "genres-0"
                else -> ""
            }
        },
    )
    override fun observeSection(kind: CatalogSectionKind): Flow<CatalogSection> =
        flowOf(CatalogSection(kind, collections.filter { it.kind == kind }))
}
