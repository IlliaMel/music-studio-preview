package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The time, the transport buttons and the shuffle and repeat switches. Text buttons stand in for
 * the kit's player controls, which come with the player feature.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    position: String,
    duration: String,
    shuffle: Boolean,
    repeat: RepeatMode,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StudioText(
            text = stringResource(R.string.player_time, position, duration),
            style = StudioTheme.typography.bodySmall,
            color = StudioTheme.colors.content.secondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            StudioButton(stringResource(R.string.player_previous), onPreviousClick, style = StudioButtonStyle.Secondary)
            StudioButton(
                text = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                onClick = onPlayPauseClick,
            )
            StudioButton(stringResource(R.string.player_next), onNextClick, style = StudioButtonStyle.Secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            StudioFilterChip(
                text = stringResource(R.string.player_shuffle),
                selected = shuffle,
                onClick = onShuffleClick,
                leadingIcon = StudioIcons.Shuffle,
            )
            StudioFilterChip(
                text = stringResource(
                    when (repeat) {
                        RepeatMode.Off -> R.string.player_repeat
                        RepeatMode.All -> R.string.player_repeat_all
                        RepeatMode.One -> R.string.player_repeat_one
                    },
                ),
                selected = repeat != RepeatMode.Off,
                onClick = onRepeatClick,
                leadingIcon = StudioIcons.Refresh,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerControlsPreview() {
    PreviewScreen {
        PlayerControls(
            isPlaying = true,
            position = "0:17",
            duration = "3:12",
            shuffle = true,
            repeat = RepeatMode.All,
            onPlayPauseClick = {},
            onPreviousClick = {},
            onNextClick = {},
            onShuffleClick = {},
            onRepeatClick = {},
        )
    }
}
