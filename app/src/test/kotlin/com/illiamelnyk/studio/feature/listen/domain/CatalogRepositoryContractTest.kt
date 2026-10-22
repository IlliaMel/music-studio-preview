package com.illiamelnyk.studio.feature.listen.domain

import com.illiamelnyk.studio.core.domain.CoverSeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogRepositoryContractTest {
    @Test fun defaultSectionProjectionFiltersKindsInsteadOfRepeatingEntireCatalog() = runTest {
        val station = CatalogCollection(
            "station",
            "Station",
            CoverSeed("station"),
            emptyList(),
            isStation = true,
            kind = CatalogSectionKind.Stations,
        )
        val new = CatalogCollection("new", "New", CoverSeed("new"), emptyList(), kind = CatalogSectionKind.New)
        val unspecified = CatalogCollection("other", "Other", CoverSeed("other"), emptyList())
        val repository = object : CatalogRepository {
            override suspend fun refresh() = Unit
            override fun observeCollections() = flowOf(listOf(station, new, unspecified))
            override fun observeCollection(id: String) = flowOf<CatalogCollection?>(null)
        }
        assertEquals(listOf(station), repository.observeSection(CatalogSectionKind.Stations).first().collections)
        assertEquals(listOf(new), repository.observeSection(CatalogSectionKind.New).first().collections)
        assertEquals(
            emptyList<CatalogCollection>(),
            repository.observeSection(CatalogSectionKind.Top).first().collections,
        )
    }
}
