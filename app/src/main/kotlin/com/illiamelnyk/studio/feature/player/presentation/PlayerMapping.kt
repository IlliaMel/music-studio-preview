package com.illiamelnyk.studio.feature.player.presentation

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricLine as SyncedLine
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.presentation.track.toUi
import com.illiamelnyk.studio.core.ui.components.LyricLine
import com.illiamelnyk.studio.core.ui.components.LyricWord
import kotlinx.collections.immutable.toImmutableList

/**
 * The player after [playback] changed: what plays, the controls and what plays next follow it; the
 * lyrics, the like and the open panels are someone else's business and stay. With nothing loaded,
 * no sheet stays open.
 */
fun PlayerContent.withPlayback(playback: PlaybackState): PlayerContent {
    val nowPlaying = playback.toNowPlaying()
    return copy(
        track = nowPlaying,
        isPlaying = playback.isPlaying,
        hasNext = playback.hasNext,
        canRemix = playback.current?.remixTrackId == null,
        isStation = playback.isStation,
        shuffle = playback.shuffle,
        repeat = playback.repeat != RepeatMode.Off,
        upNext = playback.queue.drop(playback.currentIndex + 1).map(Track::toUi).toImmutableList(),
        sheet = sheet.takeIf { nowPlaying != null && !playback.isStation },
    )
}

fun PlaybackState.toClock(): PlaybackClock = PlaybackClock(positionMs = positionMs, durationMs = durationMs)

/** The lyrics of a song with vocals: synced lines, or none when there are none or they failed to load. */
fun Outcome<List<SyncedLine>>.toLyricsUi(): LyricsUi {
    val lines = (this as? Outcome.Success)?.value.orEmpty()
    if (lines.isEmpty()) return LyricsUi.None
    return LyricsUi.Synced(lines.map { it.toUi() }.toImmutableList())
}

private fun SyncedLine.toUi(): LyricLine = LyricLine(startMs, words.map { LyricWord(it.text, it.startMs) })
