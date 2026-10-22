package com.illiamelnyk.studio.feature.listen.presentation.collection

import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.feature.listen.data.demo.DemoCatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollectionViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun trackTapStartsCollectionQueueAtSelectedTrackAndOpensPlayer() = runTest(main.testDispatcher) {
        val playback = FakePlaybackController(backgroundScope)
        val vm = CollectionViewModel("new-0", DemoCatalogRepository(), playback)
        runCurrent()
        val trackId = vm.state.value.tracks[2].id
        vm.effects.test {
            vm.onIntent(CollectionIntent.TrackClicked(trackId))
            assertEquals(CollectionEffect.OpenPlayer, awaitItem())
            assertEquals(trackId, playback.state.value.current?.id)
            assertEquals(vm.state.value.tracks.map { it.id }, playback.state.value.queue.map { it.id })
            assertFalse(playback.state.value.shuffle)
        }
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.related.none { it.id == "new-0" })
    }

    @Test fun unknownTrackDoesNotOpenPlayerOrReplaceQueue() = runTest(main.testDispatcher) {
        val playback = FakePlaybackController(backgroundScope)
        playback.play(DemoCatalog.tracks.take(2))
        val vm = CollectionViewModel("new-0", DemoCatalogRepository(), playback)
        runCurrent()
        val before = playback.state.value
        vm.effects.test {
            vm.onIntent(CollectionIntent.TrackClicked(TrackId("missing")))
            expectNoEvents()
            assertEquals(before, playback.state.value)
        }
    }

    @Test fun missingCollectionShowsRetryInsteadOfEndlessLoading() = runTest(main.testDispatcher) {
        val vm = CollectionViewModel("missing", DemoCatalogRepository(), FakePlaybackController(backgroundScope))
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.failed)
        assertTrue(vm.state.value.tracks.isEmpty())
    }

    @Test fun retryAndFailureCannotReplayAnOldCollectionQueueOrOpenItsPicker() = runTest(main.testDispatcher) {
        val collection = CatalogCollection(
            "collection",
            "Collection",
            DemoCatalog.tracks.first().coverSeed,
            DemoCatalog.tracks.take(2),
        )
        var failed = false
        val catalog = object : CatalogRepository {
            override suspend fun refresh() = Unit
            override fun observeCollections() = flowOf(listOf(collection))
            override fun observeCollection(id: String) = flow {
                if (failed) error("Refresh failed")
                emit(collection)
            }
        }
        val playback = FakePlaybackController(backgroundScope)
        val vm = CollectionViewModel("collection", catalog, playback)
        runCurrent()
        val oldTrack = vm.state.value.tracks.first().id
        failed = true
        vm.effects.test {
            vm.onIntent(CollectionIntent.Retry)
            vm.onIntent(CollectionIntent.PlayClicked)
            vm.onIntent(CollectionIntent.TrackClicked(oldTrack))
            vm.onIntent(CollectionIntent.TrackMoreClicked(oldTrack))
            expectNoEvents()
            assertTrue(playback.state.value.queue.isEmpty())
            assertEquals(null, vm.state.value.pickerTrack)
            runCurrent()
            assertTrue(vm.state.value.failed)
            assertTrue(vm.state.value.tracks.isEmpty())
            vm.onIntent(CollectionIntent.ShuffleClicked)
            vm.onIntent(CollectionIntent.TrackClicked(oldTrack))
            expectNoEvents()
            assertTrue(playback.state.value.queue.isEmpty())
        }
    }
}
