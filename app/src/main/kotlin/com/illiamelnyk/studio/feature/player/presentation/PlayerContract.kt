package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.playback.currentLyricPosition
import com.illiamelnyk.studio.core.presentation.millisToClockText

/**
 * The full player.
 *
 * @param track `null` when nothing is loaded.
 * @param lyricLine the line being sung, `null` for an instrumental or before the first line.
 */
@Immutable
data class PlayerState(
    val track: NowPlayingUi? = null,
    val isPlaying: Boolean = false,
    val position: String = "0:00",
    val duration: String = "0:00",
    val lyricLine: String? = null,
    val nextLyricLine: String? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
)

/** What the player shows for [playback], with [lyrics] of the current track. */
fun PlaybackState.toPlayerState(lyrics: List<LyricLine>): PlayerState {
    val lineIndex = currentLyricPosition(lyrics, positionMs)?.lineIndex
    return PlayerState(
        track = current?.toNowPlaying(),
        isPlaying = isPlaying,
        position = positionMs.millisToClockText(),
        duration = durationMs.millisToClockText(),
        lyricLine = lineIndex?.let { lyrics[it].text },
        nextLyricLine = lyrics.getOrNull(lineIndex?.plus(1) ?: 0)?.text,
        shuffle = shuffle,
        repeat = repeat,
    )
}

sealed interface PlayerIntent {
    data object BackClicked : PlayerIntent

    data object PlayPauseClicked : PlayerIntent

    data object NextClicked : PlayerIntent

    data object PreviousClicked : PlayerIntent

    data object ShuffleClicked : PlayerIntent

    /** Off → all → one → off. */
    data object RepeatClicked : PlayerIntent
}

sealed interface PlayerEffect {
    data object Close : PlayerEffect
}
