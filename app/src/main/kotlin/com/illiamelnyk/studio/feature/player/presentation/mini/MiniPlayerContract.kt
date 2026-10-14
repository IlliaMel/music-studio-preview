package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi

/**
 * The mini player over the screens.
 *
 * @param track `null` when nothing is loaded: the mini player hides. A station shows its name.
 * @param isStation a station shows a stop button and no progress.
 * @param hasNext whether "next" has a track to go to.
 * @param progress how far into the track, from 0 to 1. It changes four times a second, so the UI
 *   reads it only where it draws the progress line.
 */
@Immutable
data class MiniPlayerState(
    val track: NowPlayingUi? = null,
    val isPlaying: Boolean = false,
    val isStation: Boolean = false,
    val hasNext: Boolean = false,
    val progress: Float = 0f,
)

sealed interface MiniPlayerIntent {
    /** Play or pause; on a station, stop. */
    data object PlayPauseClicked : MiniPlayerIntent

    data object NextClicked : MiniPlayerIntent
}
