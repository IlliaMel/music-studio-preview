package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.feature.player.presentation.toNowPlaying
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MiniPlayerViewModel(private val playback: PlaybackController) :
    MviViewModel<MiniPlayerState, MiniPlayerIntent, Nothing>(playback.state.value.toMiniPlayerState()) {

    init {
        playback.state
            .onEach { playbackState -> setState { playbackState.toMiniPlayerState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: MiniPlayerIntent) = when (intent) {
        // A station can't pause: its button stops it.
        MiniPlayerIntent.PlayPauseClicked -> if (state.value.isStation) playback.stop() else playback.togglePlayPause()

        MiniPlayerIntent.NextClicked -> playback.next()
    }
}

fun PlaybackState.toMiniPlayerState(): MiniPlayerState {
    val duration = durationMs
    return MiniPlayerState(
        track = toNowPlaying(),
        isPlaying = isPlaying,
        isStation = isStation,
        hasNext = hasNext,
        progress = if (duration > 0) positionMs.toFloat() / duration else 0f,
    )
}
