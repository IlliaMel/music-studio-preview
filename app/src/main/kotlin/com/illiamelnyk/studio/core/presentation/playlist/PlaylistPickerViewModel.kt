package com.illiamelnyk.studio.core.presentation.playlist

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PlaylistPickerViewModel(
    private val track: Track,
    private val playlists: PlaylistRepository,
    private val tracks: TrackRepository,
) : MviViewModel<PlaylistPickerState, PlaylistPickerIntent, Unit>(
    PlaylistPickerState(title = track.title, isLiked = track.isLiked),
) {
    init {
        tracks.observeById(track.id).onEach { current ->
            if (current != null) setState { copy(isLiked = current.isLiked) }
        }.launchIn(viewModelScope)
        playlists.observeAll().onEach { list ->
            setState {
                copy(
                    playlists = list.map {
                        PlaylistChoice(
                            it.id,
                            it.name,
                            it.tracks.any { member ->
                                member.id ==
                                    track.id
                            },
                            it.tracks.firstOrNull()?.coverSeed?.value ?: it.name,
                        )
                    }.toImmutableList(),
                )
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: PlaylistPickerIntent) = when (intent) {
        is PlaylistPickerIntent.TogglePlaylist -> toggle(intent.id)

        PlaylistPickerIntent.LikeClicked -> like()

        PlaylistPickerIntent.NewPlaylist -> setState { copy(isCreating = true, failed = false) }

        is PlaylistPickerIntent.NameChanged -> setState {
            copy(name = intent.value, nameInvalid = false, failed = false)
        }

        PlaylistPickerIntent.Create -> create()
    }

    private fun like() {
        if (state.value.isBusy) return
        val liked = !state.value.isLiked
        setState { copy(isBusy = true, failed = false) }
        viewModelScope.launch {
            when (tracks.setLiked(track.id, liked)) {
                is Outcome.Success -> setState { copy(isBusy = false, isLiked = liked) }
                is Outcome.Failure -> setState { copy(isBusy = false, failed = true) }
            }
        }
    }

    private fun toggle(id: String) {
        if (state.value.isBusy) return
        val choice = state.value.playlists.find { it.id == id } ?: return
        setState { copy(isBusy = true, failed = false) }
        viewModelScope.launch {
            val result = if (choice.containsTrack) {
                playlists.removeTrack(
                    id,
                    track.id,
                )
            } else {
                playlists.addTrack(id, track)
            }
            setState { copy(isBusy = false, failed = result is Outcome.Failure) }
        }
    }

    private fun create() {
        if (state.value.isBusy) return
        val name = state.value.name.trim()
        if (name.length !in 1..80) {
            setState { copy(nameInvalid = true) }
            return
        }
        setState { copy(isBusy = true, failed = false) }
        viewModelScope.launch {
            when (val result = playlists.create(name)) {
                is Outcome.Failure -> setState { copy(isBusy = false, failed = true) }

                is Outcome.Success -> {
                    val added = playlists.addTrack(result.value.id, track)
                    setState { copy(isBusy = false, failed = added is Outcome.Failure, isCreating = false, name = "") }
                }
            }
        }
    }
}
