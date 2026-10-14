package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.VerifiedMark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewData
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/** The title and the artist, and the switches for the lyrics and the queue at the end. */
@Composable
fun PlayerTrackInfo(
    track: NowPlayingUi,
    showLyrics: Boolean,
    onLyricsClick: (() -> Unit)?,
    onQueueClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            StudioText(
                track.title,
                StudioTheme.typography.titleLarge,
                color = colors.overlay.onMediaPrimary,
                maxLines = 2,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioText(
                    text = track.subtitle,
                    style = StudioTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    color = colors.overlay.onMediaSecondary,
                    maxLines = 1,
                )
                if (track.artistVerified) VerifiedMark()
            }
        }
        if (onLyricsClick != null) {
            GlassIconButton(
                icon = StudioIcons.Forum,
                contentDescription = stringResource(R.string.player_lyrics),
                onClick = onLyricsClick,
                selected = showLyrics,
            )
        }
        if (onQueueClick !=
            null
        ) {
            GlassIconButton(StudioIcons.PlaylistPlay, stringResource(R.string.player_queue), onQueueClick)
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerTrackInfoPreview() {
    PlayerPreviewSurface {
        PlayerTrackInfo(
            track = PlayerPreviewData.glassHarbor.copy(artistVerified = true),
            showLyrics = true,
            onLyricsClick = {},
            onQueueClick = {},
        )
    }
}
