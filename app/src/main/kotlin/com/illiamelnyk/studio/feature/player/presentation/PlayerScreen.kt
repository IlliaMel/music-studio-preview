package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerArtwork
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerControls
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerLyrics

/** The full player: the track, the line being sung and the controls. */
@Composable
fun PlayerScreen(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        BackTopBar(onBack = { onIntent(PlayerIntent.BackClicked) })
        val track = state.track
        if (track == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = StudioIcons.MusicNote,
                    title = stringResource(R.string.player_nothing_playing),
                    message = stringResource(R.string.player_nothing_playing_message),
                )
            }
            return@Column
        }
        PlayerArtwork(track = track, modifier = Modifier.weight(1f))
        PlayerLyrics(line = state.lyricLine, nextLine = state.nextLyricLine)
        PlayerControls(
            isPlaying = state.isPlaying,
            position = state.position,
            duration = state.duration,
            shuffle = state.shuffle,
            repeat = state.repeat,
            onPlayPauseClick = { onIntent(PlayerIntent.PlayPauseClicked) },
            onPreviousClick = { onIntent(PlayerIntent.PreviousClicked) },
            onNextClick = { onIntent(PlayerIntent.NextClicked) },
            onShuffleClick = { onIntent(PlayerIntent.ShuffleClicked) },
            onRepeatClick = { onIntent(PlayerIntent.RepeatClicked) },
        )
    }
}

@PreviewLightDark
@Composable
private fun PlayerScreenPreview(@PreviewParameter(PlayerPreviewData::class) state: PlayerState) {
    PreviewScreen { PlayerScreen(state = state, onIntent = {}) }
}
