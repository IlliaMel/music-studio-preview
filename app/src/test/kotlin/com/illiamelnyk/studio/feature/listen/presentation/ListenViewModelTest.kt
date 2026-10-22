package com.illiamelnyk.studio.feature.listen.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogSection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ListenViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun stalledSectionTimesOutIndependentlyAndRetryLoadsIt() = runTest(main.testDispatcher) {
        val catalog = SectionCatalog()
        catalog.stalled = CatalogSectionKind.New
        val vm = ListenViewModel(catalog, FakePlaybackController(backgroundScope))
        runCurrent()
        assertFalse(vm.state.value.sections.first().isLoading)
        assertTrue(vm.state.value.sections[1].isLoading)
        advanceTimeBy(7_999)
        runCurrent()
        assertTrue(vm.state.value.sections[1].isLoading)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(vm.state.value.sections[1].failed)
        assertFalse(vm.state.value.sections[1].isLoading)
        assertEquals(1, vm.state.value.sections.first().collections.size)
        catalog.stalled = null
        vm.onIntent(ListenIntent.RetrySection(CatalogSectionKind.New))
        runCurrent()
        assertFalse(vm.state.value.sections[1].failed)
        assertEquals("new", vm.state.value.sections[1].collections.single().id)
    }

    @Test fun slowCatalogKeepsEverySectionLoadingWithoutBlockingLocalActions() = runTest(main.testDispatcher) {
        val catalog = SectionCatalog().apply { delayMillis = 5_000 }
        val vm = ListenViewModel(catalog, FakePlaybackController(backgroundScope))
        assertTrue(vm.state.value.sections.all { it.isLoading && !it.failed && it.collections.isEmpty() })
        runCurrent()
        vm.onIntent(ListenIntent.SearchClicked)
        assertTrue(vm.state.value.searchVisible)
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(vm.state.value.sections.all { it.isLoading && !it.failed })
        advanceTimeBy(1)
        runCurrent()
        assertTrue(vm.state.value.sections.all { !it.isLoading && !it.failed && it.collections.size == 1 })
    }

    @Test fun failedSectionRetryClearsOnlyItsError() = runTest(main.testDispatcher) {
        val catalog = SectionCatalog()
        catalog.failed = true
        val vm = ListenViewModel(catalog, FakePlaybackController(backgroundScope))
        runCurrent()
        assertTrue(vm.state.value.sections.all { it.failed && !it.isLoading })
        catalog.failed = false
        vm.onIntent(ListenIntent.RetrySection(CatalogSectionKind.Top))
        runCurrent()
        assertFalse(vm.state.value.sections[2].failed)
        assertTrue(vm.state.value.sections[1].failed)
    }

    @Test fun stationStartsLivePlaybackAndOpensPlayerWhileMixOpensCollection() = runTest(main.testDispatcher) {
        val playback = FakePlaybackController(backgroundScope)
        val vm = ListenViewModel(SectionCatalog(), playback)
        runCurrent()
        vm.effects.test {
            vm.onIntent(ListenIntent.CollectionClicked("stations"))
            assertEquals(ListenEffect.OpenPlayer, awaitItem())
            assertEquals("Stations", playback.state.value.stationName)
            assertTrue(playback.state.value.shuffle)
            assertEquals(DemoCatalog.tracks.take(2), playback.state.value.queue)
            vm.onIntent(ListenIntent.CollectionClicked("new"))
            assertEquals(ListenEffect.OpenCollection("new"), awaitItem())
        }
    }

    @Test fun refreshingOrRemovedStationCannotStartPlaybackFromAStaleClick() = runTest(main.testDispatcher) {
        val catalog = SectionCatalog()
        val playback = FakePlaybackController(backgroundScope)
        val vm = ListenViewModel(catalog, playback)
        runCurrent()
        catalog.hidden = CatalogSectionKind.Stations
        vm.effects.test {
            vm.onIntent(ListenIntent.RetrySection(CatalogSectionKind.Stations))
            vm.onIntent(ListenIntent.CollectionClicked("stations"))
            expectNoEvents()
            assertTrue(playback.state.value.queue.isEmpty())
            runCurrent()
            assertTrue(vm.state.value.sections.first().collections.isEmpty())
            vm.onIntent(ListenIntent.CollectionClicked("stations"))
            expectNoEvents()
            assertTrue(playback.state.value.queue.isEmpty())
        }
    }
}

private class SectionCatalog : CatalogRepository {
    var stalled: CatalogSectionKind? = null
    var failed = false
    var delayMillis = 0L
    var hidden: CatalogSectionKind? = null
    override suspend fun refresh() = Unit
    override fun observeCollections(): Flow<List<CatalogCollection>> = flowOf(emptyList())
    override fun observeCollection(id: String): Flow<CatalogCollection?> = flowOf(null)
    override fun observeSection(kind: CatalogSectionKind): Flow<CatalogSection> = flow {
        delay(delayMillis)
        if (failed) error("Demo error")
        if (kind == stalled) awaitCancellation()
        emit(
            CatalogSection(
                kind,
                if (kind == hidden) {
                    emptyList()
                } else {
                    listOf(
                        CatalogCollection(
                            kind.name.lowercase(),
                            kind.name,
                            CoverSeed(kind.name),
                            DemoCatalog.tracks.take(2),
                            kind == CatalogSectionKind.Stations,
                        ),
                    )
                },
            ),
        )
    }
}
