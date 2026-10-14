package com.illiamelnyk.studio.feature.player.presentation.mini

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi

/**
 * The mini player over the screens.
 *
 * @param track `null` when nothing is loaded: the mini player hides.
 * @param progress how far into the track, from 0 to 1. It changes four times a second, so the UI
 *   reads it only where it draws the progress line.
 */
@Immutable
data class MiniPlayerState(val track: NowPlayingUi? = null, val isPlaying: Boolean = false, val progress: Float = 0f)

sealed interface MiniPlayerIntent {
    data object PlayPauseClicked : MiniPlayerIntent
}
