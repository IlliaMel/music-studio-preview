package com.illiamelnyk.studio.feature.player.presentation.mini.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi

/**
 * A stand-in for the kit's MiniPlayer: a 56 dp pill with the round cover, the title and artist,
 * play or pause, and a thin progress line along the bottom. Tapping it opens the full player.
 *
 * @param progress read only while drawing the line, from 0 to 1.
 */
@Composable
fun MiniPlayerBar(
    track: NowPlayingUi,
    isPlaying: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.full
    Row(
        // clickable before the container, so the press scale shrinks the whole pill.
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.size.component.miniPlayer)
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.player_open), onClick = onClick)
            .background(colors.surface.level3)
            .border(dimens.size.component.divider, colors.outline.subtle, shape)
            .drawWithContent {
                drawContent()
                val lineHeight = dimens.spacing.hairline.toPx()
                drawRect(
                    color = colors.content.primary,
                    topLeft = Offset(0f, size.height - lineHeight),
                    size = Size(size.width * progress().coerceIn(0f, 1f), lineHeight),
                )
            }
            .padding(start = dimens.spacing.xs, end = dimens.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(seed = track.coverSeed, size = dimens.size.cover.xs, shape = shape)
        Column(Modifier.weight(1f)) {
            StudioText(track.title, StudioTheme.typography.titleSmall, maxLines = 1)
            StudioText(track.artist, StudioTheme.typography.bodySmall, color = colors.content.secondary, maxLines = 1)
        }
        StudioButton(
            text = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            onClick = onPlayPauseClick,
            style = StudioButtonStyle.Text,
            size = StudioButtonSize.Small,
        )
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerBarPreview() {
    PreviewScreen {
        MiniPlayerBar(
            track = NowPlayingUi(title = "Glass Harbor", artist = "Mira Vale", coverSeed = "Glass Harbor"),
            isPlaying = true,
            progress = { 0.4f },
            onClick = {},
            onPlayPauseClick = {},
            modifier = Modifier.padding(StudioTheme.dimens.spacing.m),
        )
    }
}
