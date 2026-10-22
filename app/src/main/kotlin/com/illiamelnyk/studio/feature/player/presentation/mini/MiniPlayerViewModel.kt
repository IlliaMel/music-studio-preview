package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.feature.player.presentation.toNowPlaying
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MiniPlayerViewModel(
    private val playback: PlaybackController,
    private val drafts: ComposerDraftStore = ComposerDraftStore(),
) : MviViewModel<MiniPlayerState, MiniPlayerIntent, Nothing>(playback.state.value.toMiniPlayerState()) {

    init {
        playback.state
            .onEach { playbackState -> setState { playbackState.toMiniPlayerState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: MiniPlayerIntent) = when (intent) {
        // A station can't pause: its button stops it.
        MiniPlayerIntent.PlayPauseClicked -> if (state.value.isStation) playback.stop() else playback.togglePlayPause()

        MiniPlayerIntent.NextClicked -> playback.next()

        MiniPlayerIntent.RemixClicked -> remixCurrentTrack()
    }

    private fun remixCurrentTrack() {
        val current = playback.state.value
        if (!current.isStation) current.current?.takeIf { it.canRemixOriginal }?.let(drafts::remix)
    }
}

fun PlaybackState.toMiniPlayerState(): MiniPlayerState = MiniPlayerState(
    track = toNowPlaying(),
    isPlaying = isPlaying,
    isStation = isStation,
    hasNext = hasNext,
    canRemix = !isStation && current?.canRemixOriginal == true,
)
