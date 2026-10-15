package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.components.PlayerControls
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlaybackClock
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/**
 * The seek bar and the transport of a track, or, on a station, which can't be paused or sought, its
 * own LIVE status and stop control.
 *
 * @param repeat the kit's switch is on or off; see [com.illiamelnyk.studio.feature.player.presentation.PlayerContent.repeat].
 */
@Composable
fun PlayerTransport(
    isStation: Boolean,
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: Boolean,
    clock: () -> PlaybackClock,
    onSeek: (fraction: Float) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hasNext: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
        if (isStation) {
            StationControls(onStop = onStop)
        } else {
            PlayerSeekBar(clock = clock, onSeek = onSeek)
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                nextEnabled = hasNext,
                shuffle = shuffle,
                onShuffleChange = onShuffleChange,
                repeat = repeat,
                onRepeatChange = onRepeatChange,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerTransportPreview() {
    PlayerPreviewSurface {
        PlayerTransport(
            isStation = false,
            isPlaying = true,
            shuffle = true,
            repeat = false,
            clock = { PlaybackClock(positionMs = 17_000, durationMs = 192_000) },
            onSeek = {},
            onPlayPause = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onShuffleChange = {},
            onRepeatChange = {},
        )
        PlayerTransport(
            isStation = true,
            isPlaying = true,
            shuffle = false,
            repeat = false,
            clock = { PlaybackClock() },
            onSeek = {},
            onPlayPause = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onShuffleChange = {},
            onRepeatChange = {},
        )
    }
}
