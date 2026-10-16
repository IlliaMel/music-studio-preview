package com.illiamelnyk.studio.feature.library.presentation

import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakePlaylistRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import com.illiamelnyk.studio.testing.MemoryTrackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun externalLikeChangesUpdateLibraryAndLikedPlaybackUsesOnlyLikedQueue() = runTest(main.testDispatcher) {
        val tracks = MemoryTrackRepository()
        val playback = FakePlaybackController(backgroundScope)
        val vm = LibraryViewModel(tracks, playback, FakePlaylistRepository())
        runCurrent()
        assertTrue(vm.state.value.liked.isEmpty())
        tracks.setLiked(FakeCatalog.tracks[0].id, true)
        tracks.setLiked(FakeCatalog.tracks[1].id, true)
        runCurrent()
        assertEquals(listOf(FakeCatalog.tracks[0].id, FakeCatalog.tracks[1].id), vm.state.value.liked.map { it.id })
        vm.effects.test {
            vm.onIntent(LibraryIntent.TrackClicked(FakeCatalog.tracks[1].id))
            assertEquals(LibraryEffect.OpenPlayer, awaitItem())
            assertEquals(FakeCatalog.tracks[1].id, playback.state.value.current?.id)
            assertEquals(2, playback.state.value.queue.size)
        }
        tracks.setLiked(FakeCatalog.tracks[0].id, false)
        runCurrent()
        assertEquals(listOf(FakeCatalog.tracks[1].id), vm.state.value.liked.map { it.id })
    }

    @Test fun playlistSelectionPlaysItsMembersAndMoreSelectsCorrectTrack() = runTest(main.testDispatcher) {
        val playlists = FakePlaylistRepository()
        val playlist = (playlists.create("Drive") as Outcome.Success).value
        playlists.addTrack(playlist.id, FakeCatalog.tracks[3])
        val playback = FakePlaybackController(backgroundScope)
        val vm = LibraryViewModel(MemoryTrackRepository(), playback, playlists)
        runCurrent()
        vm.onIntent(LibraryIntent.TabSelected(LibraryTab.Playlists))
        vm.onIntent(LibraryIntent.PlaylistSelected(playlist.id))
        vm.effects.test {
            vm.onIntent(LibraryIntent.TrackClicked(FakeCatalog.tracks[3].id))
            assertEquals(LibraryEffect.OpenPlayer, awaitItem())
            assertEquals(listOf(FakeCatalog.tracks[3]), playback.state.value.queue)
        }
        vm.onIntent(LibraryIntent.TrackMoreClicked(FakeCatalog.tracks[3].id))
        assertEquals(FakeCatalog.tracks[3], vm.state.value.pickerTrack)
        vm.onIntent(LibraryIntent.BackClicked)
        assertEquals(null, vm.state.value.selectedPlaylistId)
    }

    @Test fun validCreateTrimsNameAndOpensNewPlaylistWhileBlankNameStaysInEditor() = runTest(main.testDispatcher) {
        val playlists = FakePlaylistRepository()
        val vm = LibraryViewModel(MemoryTrackRepository(), FakePlaybackController(backgroundScope), playlists)
        vm.onIntent(LibraryIntent.NewPlaylist)
        vm.onIntent(LibraryIntent.PlaylistNameChanged("  "))
        vm.onIntent(LibraryIntent.CreatePlaylist)
        runCurrent()
        assertTrue(vm.state.value.nameInvalid)
        assertTrue(playlists.observeAll().first().isEmpty())
        vm.onIntent(LibraryIntent.PlaylistNameChanged("  Morning  "))
        vm.onIntent(LibraryIntent.CreatePlaylist)
        runCurrent()
        assertFalse(vm.state.value.showCreatePlaylist)
        assertEquals(LibraryTab.Playlists, vm.state.value.tab)
        assertEquals("Morning", playlists.observeAll().first().single().name)
        assertEquals(playlists.observeAll().first().single().id, vm.state.value.selectedPlaylistId)
    }

    @Test fun delayedCreateAfterDismissAndTabChangeStoresPlaylistWithoutRedirecting() = runTest(main.testDispatcher) {
        val repository = FakePlaylistRepository()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val delayed = object : PlaylistRepository by repository {
            override suspend fun create(name: String): Outcome<Playlist> {
                calls++
                gate.await()
                return repository.create(name)
            }
        }
        val vm = LibraryViewModel(MemoryTrackRepository(), FakePlaybackController(backgroundScope), delayed)
        vm.onIntent(LibraryIntent.NewPlaylist)
        vm.onIntent(LibraryIntent.PlaylistNameChanged("Saved mix"))
        vm.onIntent(LibraryIntent.CreatePlaylist)
        vm.onIntent(LibraryIntent.CreatePlaylist)
        runCurrent()
        assertEquals(1, calls)
        vm.onIntent(LibraryIntent.DismissCreate)
        vm.onIntent(LibraryIntent.TabSelected(LibraryTab.Liked))
        gate.complete(Unit)
        runCurrent()
        assertEquals("Saved mix", repository.observeAll().first().single().name)
        assertEquals(LibraryTab.Liked, vm.state.value.tab)
        assertEquals(null, vm.state.value.selectedPlaylistId)
        assertFalse(vm.state.value.isBusy)
        assertFalse(vm.state.value.showCreatePlaylist)
    }

    @Test fun oldCreateFailureDoesNotMarkReopenedDialogAndCurrentFailureCanRetry() = runTest(main.testDispatcher) {
        val repository = FakePlaylistRepository()
        var gate: CompletableDeferred<Outcome<Playlist>>? = CompletableDeferred()
        val delayed = object : PlaylistRepository by repository {
            override suspend fun create(name: String): Outcome<Playlist> = gate?.await() ?: repository.create(name)
        }
        val vm = LibraryViewModel(MemoryTrackRepository(), FakePlaybackController(backgroundScope), delayed)
        vm.onIntent(LibraryIntent.NewPlaylist)
        vm.onIntent(LibraryIntent.PlaylistNameChanged("Old"))
        vm.onIntent(LibraryIntent.CreatePlaylist)
        runCurrent()
        vm.onIntent(LibraryIntent.DismissCreate)
        vm.onIntent(LibraryIntent.NewPlaylist)
        vm.onIntent(LibraryIntent.PlaylistNameChanged("Fresh"))
        gate!!.complete(Outcome.Failure(AppError.Network))
        runCurrent()
        assertTrue(vm.state.value.showCreatePlaylist)
        assertEquals("Fresh", vm.state.value.playlistName)
        assertFalse(vm.state.value.failed)
        assertFalse(vm.state.value.isBusy)
        gate = CompletableDeferred()
        vm.onIntent(LibraryIntent.CreatePlaylist)
        runCurrent()
        gate!!.complete(Outcome.Failure(AppError.Network))
        runCurrent()
        assertTrue(vm.state.value.failed)
        gate = null
        vm.onIntent(LibraryIntent.CreatePlaylist)
        runCurrent()
        assertFalse(vm.state.value.failed)
        assertEquals(repository.observeAll().first().single().id, vm.state.value.selectedPlaylistId)
    }
}
