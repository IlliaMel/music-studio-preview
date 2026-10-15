package com.illiamelnyk.studio.core.presentation.playlist

import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.data.fake.FakePlaylistRepository
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.testing.MemoryTrackRepository
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaylistPickerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun createAddsSelectedTrackAndRemovalUpdatesMembership() = runTest(main.testDispatcher) {
        val repository = FakePlaylistRepository()
        val track = FakeCatalog.tracks.first()
        val vm = PlaylistPickerViewModel(track, repository, MemoryTrackRepository())
        runCurrent()
        vm.onIntent(PlaylistPickerIntent.NewPlaylist)
        vm.onIntent(PlaylistPickerIntent.NameChanged("  Night drive  "))
        vm.onIntent(PlaylistPickerIntent.Create)
        vm.onIntent(PlaylistPickerIntent.Create)
        runCurrent()
        val playlist = repository.observeAll().first().single()
        assertEquals("Night drive", playlist.name)
        assertEquals(listOf(track), playlist.tracks)
        assertTrue(vm.state.value.playlists.single().containsTrack)
        assertFalse(vm.state.value.isCreating)
        vm.onIntent(PlaylistPickerIntent.TogglePlaylist(playlist.id))
        runCurrent()
        assertTrue(repository.observeAll().first().single().tracks.isEmpty())
        assertFalse(vm.state.value.playlists.single().containsTrack)
    }

    @Test fun invalidNameKeepsEditorOpenAndCreatesNothing() = runTest(main.testDispatcher) {
        val repository = FakePlaylistRepository()
        val vm = PlaylistPickerViewModel(FakeCatalog.tracks.first(), repository, MemoryTrackRepository())
        vm.onIntent(PlaylistPickerIntent.NewPlaylist)
        vm.onIntent(PlaylistPickerIntent.NameChanged(" "))
        vm.onIntent(PlaylistPickerIntent.Create)
        runCurrent()
        assertTrue(vm.state.value.nameInvalid)
        assertTrue(vm.state.value.isCreating)
        assertFalse(vm.state.value.isBusy)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test fun failedAddAfterCreateKeepsPlaylistAndAllowsMembershipRetry() = runTest(main.testDispatcher) {
        val repository = FakePlaylistRepository()
        val failing = object : PlaylistRepository by repository {
            var fail = true
            override suspend fun addTrack(playlistId: String, track: Track): Outcome<Unit> = if (fail) Outcome.Failure(AppError.Network) else repository.addTrack(playlistId, track)
        }
        val vm = PlaylistPickerViewModel(FakeCatalog.tracks.first(), failing, MemoryTrackRepository())
        runCurrent()
        vm.onIntent(PlaylistPickerIntent.NewPlaylist)
        vm.onIntent(PlaylistPickerIntent.NameChanged("Night"))
        vm.onIntent(PlaylistPickerIntent.Create)
        runCurrent()
        val playlist = repository.observeAll().first().single()
        assertTrue(playlist.tracks.isEmpty())
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.isBusy)
        assertFalse(vm.state.value.isCreating)
        failing.fail = false
        vm.onIntent(PlaylistPickerIntent.TogglePlaylist(playlist.id))
        runCurrent()
        assertEquals(listOf(FakeCatalog.tracks.first()), repository.observeAll().first().single().tracks)
        assertFalse(vm.state.value.failed)
    }

    @Test fun failedLikeLeavesTrackUnlikedAndCanBeRetried() = runTest(main.testDispatcher) {
        val tracks = MemoryTrackRepository()
        tracks.failLike = true
        val track = FakeCatalog.tracks.first()
        val vm = PlaylistPickerViewModel(track, FakePlaylistRepository(), tracks)
        runCurrent()
        vm.onIntent(PlaylistPickerIntent.LikeClicked)
        runCurrent()
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.isLiked)
        assertFalse(vm.state.value.isBusy)
        tracks.failLike = false
        vm.onIntent(PlaylistPickerIntent.LikeClicked)
        runCurrent()
        assertFalse(vm.state.value.failed)
        assertTrue(vm.state.value.isLiked)
        assertTrue(tracks.observeLiked().first().any { it.id == track.id })
        tracks.setLiked(track.id, false)
        runCurrent()
        assertFalse(vm.state.value.isLiked)
    }

    @Test fun addingToExistingPlaylistKeepsOtherMembers() = runTest(main.testDispatcher) {
        val repository = FakePlaylistRepository()
        val playlist = (repository.create("Mix") as Outcome.Success).value
        repository.addTrack(playlist.id, FakeCatalog.tracks[1])
        val vm = PlaylistPickerViewModel(FakeCatalog.tracks[0], repository, MemoryTrackRepository())
        runCurrent()
        vm.onIntent(PlaylistPickerIntent.TogglePlaylist(playlist.id))
        runCurrent()
        assertEquals(listOf(FakeCatalog.tracks[1], FakeCatalog.tracks[0]), repository.observeAll().first().single().tracks)
    }
}
