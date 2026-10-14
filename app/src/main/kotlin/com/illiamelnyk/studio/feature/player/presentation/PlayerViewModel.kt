package com.illiamelnyk.studio.feature.player.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.presentation.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PlayerViewModel(private val playback: PlaybackController, private val lyrics: LyricsRepository) :
    MviViewModel<PlayerState, PlayerIntent, PlayerEffect>(PlayerState()) {

    /** The lyrics of the track that is playing, empty until they arrive. */
    private val lyricLines = MutableStateFlow<List<LyricLine>>(emptyList())

    init {
        viewModelScope.launch {
            // A new track cancels the lyrics request of the previous one.
            playback.state.map { it.current?.id }.distinctUntilChanged().collectLatest { id ->
                lyricLines.value = emptyList()
                if (id != null) lyricLines.value = (lyrics.lyrics(id) as? Outcome.Success)?.value.orEmpty()
            }
        }
        combine(playback.state, lyricLines) { playbackState, lines -> playbackState.toPlayerState(lines) }
            .onEach { playerState -> setState { playerState } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: PlayerIntent) = when (intent) {
        PlayerIntent.BackClicked -> sendEffect(PlayerEffect.Close)
        PlayerIntent.PlayPauseClicked -> playback.togglePlayPause()
        PlayerIntent.NextClicked -> playback.next()
        PlayerIntent.PreviousClicked -> playback.previous()
        PlayerIntent.ShuffleClicked -> playback.setShuffle(!state.value.shuffle)
        PlayerIntent.RepeatClicked -> playback.setRepeat(state.value.repeat.next())
    }

    private fun RepeatMode.next(): RepeatMode = RepeatMode.entries[(ordinal + 1) % RepeatMode.entries.size]
}
