package com.illiamelnyk.studio.feature.listen.presentation.collection

import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.feature.listen.data.fake.FakeCatalogRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
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
        val vm = CollectionViewModel("new-0", FakeCatalogRepository(), playback)
        runCurrent()
        val trackId = vm.state.value.tracks[2].id
        vm.effects.test {
            vm.onIntent(CollectionIntent.TrackClicked(trackId))
            assertEquals(CollectionEffect.OpenPlayer, awaitItem())
            assertEquals(trackId, playback.state.value.current?.id)
            assertEquals(6, playback.state.value.queue.size)
            assertFalse(playback.state.value.shuffle)
        }
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.related.none { it.id == "new-0" })
    }

    @Test fun unknownTrackDoesNotOpenPlayerOrReplaceQueue() = runTest(main.testDispatcher) {
        val playback = FakePlaybackController(backgroundScope)
        playback.play(FakeCatalog.tracks.take(2))
        val vm = CollectionViewModel("new-0", FakeCatalogRepository(), playback)
        runCurrent()
        val before = playback.state.value
        vm.effects.test {
            vm.onIntent(CollectionIntent.TrackClicked(TrackId("missing")))
            expectNoEvents()
            assertEquals(before, playback.state.value)
        }
    }

    @Test fun missingCollectionShowsRetryInsteadOfEndlessLoading() = runTest(main.testDispatcher) {
        val vm = CollectionViewModel("missing", FakeCatalogRepository(), FakePlaybackController(backgroundScope))
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.failed)
        assertTrue(vm.state.value.tracks.isEmpty())
    }
}
