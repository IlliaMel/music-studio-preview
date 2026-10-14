package com.illiamelnyk.studio.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.LyricLine
import com.illiamelnyk.studio.core.ui.components.LyricWord
import com.illiamelnyk.studio.core.ui.components.LyricsView
import com.illiamelnyk.studio.core.ui.components.PlayerControls
import com.illiamelnyk.studio.core.ui.components.SeekBar
import com.illiamelnyk.studio.core.ui.components.coverBackdrop
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A player on cover color with a simulated clock: the seek bar, the transport and synced lyrics all
 * follow one position that advances every frame. Only the draw phase and derived state read it.
 */
@Composable
internal fun PlayerPage() {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    var playing by rememberSaveable { mutableStateOf(true) }
    var shuffle by rememberSaveable { mutableStateOf(false) }
    var repeat by rememberSaveable { mutableStateOf(true) }
    val position = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                position.longValue = (position.longValue + now - last) % DURATION_MS
                last = now
            }
        }
    }
    val second by remember { derivedStateOf { position.longValue / MILLIS_PER_SECOND } }
    Box(
        Modifier
            .fillMaxSize()
            .coverBackdrop(SEED),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.m),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.m),
        ) {
            Cover(
                seed = SEED,
                size = dimens.size.cover.l,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .dropShadow(StudioTheme.shapes.m, StudioTheme.effects.elevation.artwork.toShadow()),
            )
            Column {
                StudioText(SEED, StudioTheme.typography.titleLarge, color = colors.overlay.onMediaPrimary)
                StudioText("Studio · 0:48", StudioTheme.typography.bodySmall, color = colors.overlay.onMediaSecondary)
            }
            SeekBar(
                progress = { position.longValue.toFloat() / DURATION_MS },
                onSeek = { fraction -> position.longValue = (fraction * DURATION_MS).toLong() },
                elapsed = clock(second),
                remaining = "-" + clock(DURATION_MS / MILLIS_PER_SECOND - second),
            )
            PlayerControls(
                isPlaying = playing,
                onPlayPause = { playing = !playing },
                onPrevious = { position.longValue = 0L },
                onNext = { position.longValue = 0L },
                shuffle = shuffle,
                onShuffleChange = { shuffle = it },
                repeat = repeat,
                onRepeatChange = { repeat = it },
            )
            LyricsView(
                lines = Lyrics,
                positionMs = { position.longValue },
                onSeek = { position.longValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

private fun clock(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private const val SEED = "Night Drive Through Kyiv"
private const val MILLIS_PER_SECOND = 1_000L
private const val LINE_MS = 4_000L

/** Twelve lines of four seconds each, every word sung at an even pace through its line. */
private val Lyrics: List<LyricLine> = listOf(
    "Radio is playing something new",
    "Streetlights are counting the miles",
    "We're chasing the river down to the bridge",
    "Hold on to the moment",
    "The city hums a lullaby",
    "Your hand is warm against the glass",
    "Nobody's waiting at the end",
    "So we drive until the morning",
    "Echoes of a summer song",
    "Fading out on the radio",
    "We're chasing the river down to the bridge",
    "Hold on, hold on",
).mapIndexed { index, line ->
    val start = index * LINE_MS
    val words = line.split(' ')
    LyricLine(start, words.mapIndexed { wordIndex, word -> LyricWord(word, start + LINE_MS * wordIndex / words.size) })
}

private val DURATION_MS = Lyrics.size * LINE_MS
