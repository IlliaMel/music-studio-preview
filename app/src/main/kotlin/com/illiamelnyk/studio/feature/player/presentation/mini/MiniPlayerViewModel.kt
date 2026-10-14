package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.feature.player.presentation.toNowPlaying
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MiniPlayerViewModel(private val playback: PlaybackController) :
    MviViewModel<MiniPlayerState, MiniPlayerIntent, Nothing>(MiniPlayerState()) {

    init {
        playback.state
            .onEach { playback ->
                val duration = playback.durationMs
                setState {
                    MiniPlayerState(
                        track = playback.current?.toNowPlaying(),
                        isPlaying = playback.isPlaying,
                        progress = if (duration > 0) playback.positionMs.toFloat() / duration else 0f,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: MiniPlayerIntent) = when (intent) {
        MiniPlayerIntent.PlayPauseClicked -> playback.togglePlayPause()
    }
}
