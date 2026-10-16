package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.millisToClockText
import com.illiamelnyk.studio.core.ui.components.SeekBar
import com.illiamelnyk.studio.feature.player.presentation.PlaybackClock
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/**
 * The kit's seek bar with the time played and the time left. The bar reads [clock] while drawing,
 * so the four ticks a second only redraw it; the times are text, which the kit takes as strings,
 * so this block recomposes once a second, when a whole second has passed.
 */
@Composable
fun PlayerSeekBar(clock: () -> PlaybackClock, onSeek: (fraction: Float) -> Unit, modifier: Modifier = Modifier) {
    val currentClock by rememberUpdatedState(clock)
    val elapsedSeconds by remember { derivedStateOf { currentClock().positionMs / MILLIS_PER_SECOND } }
    val remainingSeconds by remember {
        derivedStateOf {
            val now = currentClock()
            // Rounded up, so the two times always add up to the length of the track.
            (now.durationMs - now.positionMs + MILLIS_PER_SECOND - 1).coerceAtLeast(0) / MILLIS_PER_SECOND
        }
    }
    SeekBar(
        progress = { currentClock().fraction },
        onSeek = onSeek,
        elapsed = (elapsedSeconds * MILLIS_PER_SECOND).toPlayerClockText(),
        remaining = stringResource(
            R.string.player_time_left,
            (remainingSeconds * MILLIS_PER_SECOND).toPlayerClockText(),
        ),
        modifier = modifier,
    )
}

private fun Long.toPlayerClockText(): String = millisToClockText().padStart(5, '0')

private const val MILLIS_PER_SECOND = 1_000L

@PreviewLightDark
@Composable
private fun PlayerSeekBarPreview() {
    PlayerPreviewSurface {
        PlayerSeekBar(clock = { PlaybackClock(positionMs = 131_000, durationMs = 239_000) }, onSeek = {})
    }
}
