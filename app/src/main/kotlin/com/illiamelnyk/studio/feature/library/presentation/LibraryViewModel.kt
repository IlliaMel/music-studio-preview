package com.illiamelnyk.studio.feature.library.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.track.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class LibraryViewModel(tracks: TrackRepository, private val playback: PlaybackController) :
    MviViewModel<LibraryState, LibraryIntent, LibraryEffect>(LibraryState()) {

    private var liked: List<Track> = emptyList()

    init {
        combine(
            tracks.observeLiked().map { list -> list.filter { it.status == TrackStatus.Ready } },
            playback.state.map { it.current?.id }.distinctUntilChanged(),
        ) { list, playingId -> list to playingId }
            .onEach { (list, playingId) ->
                liked = list
                setState {
                    copy(liked = list.map(Track::toUi).toImmutableList(), playingTrackId = playingId, isLoading = false)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: LibraryIntent) = when (intent) {
        LibraryIntent.BackClicked -> sendEffect(LibraryEffect.Close)
        is LibraryIntent.TrackClicked -> play(intent.id)
    }

    private fun play(id: TrackId) {
        val index = liked.indexOfFirst { it.id == id }
        if (index >= 0) playback.play(liked, index)
    }
}
