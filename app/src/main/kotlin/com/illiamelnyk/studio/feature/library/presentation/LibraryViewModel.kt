package com.illiamelnyk.studio.feature.library.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toClockText
import kotlin.time.Duration
import com.illiamelnyk.studio.core.presentation.track.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LibraryViewModel(tracks: TrackRepository, private val playback: PlaybackController, private val playlists: PlaylistRepository) :
    MviViewModel<LibraryState, LibraryIntent, LibraryEffect>(LibraryState()) {
    private var liked: List<Track> = emptyList()
    private var lists: List<Playlist> = emptyList()

    init {
        tracks.observeLiked().onEach { list ->
            val ready = list.filter { it.status == TrackStatus.Ready }
            liked = ready
            setState { copy(liked = ready.map(Track::toUi).toImmutableList(), isLoading = false) }
        }.launchIn(viewModelScope)
        playlists.observeAll().onEach { list ->
            lists = list
            setState { copy(playlists = list.map { PlaylistUi(it.id, it.name, it.tracks.map(Track::toUi).toImmutableList(), UiText.Plural(R.plurals.listen_collection_summary, it.tracks.size, it.tracks.size, it.tracks.fold(Duration.ZERO) { total, track -> total + track.duration }.toClockText()), it.tracks.firstOrNull()?.coverSeed?.value ?: it.name) }.toImmutableList()) }
        }.launchIn(viewModelScope)
        playback.state.map { it.current?.id }.distinctUntilChanged().onEach {
            setState { copy(playingTrackId = it) }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: LibraryIntent) = when (intent) {
        LibraryIntent.BackClicked -> if (state.value.selectedPlaylistId != null) setState { copy(selectedPlaylistId = null) } else sendEffect(LibraryEffect.Close)
        LibraryIntent.ExploreClicked -> sendEffect(LibraryEffect.Explore)
        is LibraryIntent.TrackClicked -> play(intent.id)
        is LibraryIntent.TrackMoreClicked -> setState { copy(pickerTrack = queue().find { it.id == intent.id }) }
        LibraryIntent.DismissPicker -> setState { copy(pickerTrack = null) }
        is LibraryIntent.TabSelected -> setState { copy(tab = intent.tab, selectedPlaylistId = null) }
        is LibraryIntent.PlaylistSelected -> setState { copy(selectedPlaylistId = intent.id) }
        is LibraryIntent.PlayPlaylist -> playPlaylist(intent.shuffle)
        LibraryIntent.NewPlaylist -> setState { copy(showCreatePlaylist = true, failed = false) }
        LibraryIntent.DismissCreate -> setState { copy(showCreatePlaylist = false, playlistName = "", nameInvalid = false) }
        is LibraryIntent.PlaylistNameChanged -> setState { copy(playlistName = intent.name, nameInvalid = false, failed = false) }
        LibraryIntent.CreatePlaylist -> create()
    }

    private fun queue(): List<Track> = if (state.value.tab == LibraryTab.Liked) liked else lists.find { it.id == state.value.selectedPlaylistId }?.tracks.orEmpty()

    private fun play(id: TrackId) {
        val queue = queue()
        val index = queue.indexOfFirst { it.id == id }
        if (index >= 0) {
            playback.setShuffle(false)
            playback.play(queue, index)
            sendEffect(LibraryEffect.OpenPlayer)
        }
    }

    private fun playPlaylist(shuffle: Boolean) {
        val queue = queue()
        if (queue.isEmpty()) return
        playback.setShuffle(shuffle)
        playback.play(queue, if (shuffle) queue.indices.random() else 0)
        sendEffect(LibraryEffect.OpenPlayer)
    }

    private fun create() {
        if (state.value.isBusy) return
        val name = state.value.playlistName.trim()
        if (name.length !in 1..80) {
            setState { copy(nameInvalid = true) }
            return
        }
        setState { copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = playlists.create(name)) {
                is Outcome.Failure -> setState { copy(isBusy = false, failed = true) }
                is Outcome.Success -> setState { copy(isBusy = false, showCreatePlaylist = false, playlistName = "", selectedPlaylistId = result.value.id, tab = LibraryTab.Playlists) }
            }
        }
    }
}
