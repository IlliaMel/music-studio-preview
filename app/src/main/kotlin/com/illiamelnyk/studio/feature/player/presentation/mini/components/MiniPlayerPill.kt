package com.illiamelnyk.studio.feature.player.presentation.mini.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.MiniPlayer
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi
import com.illiamelnyk.studio.feature.player.presentation.components.GlassIconButton
import com.illiamelnyk.studio.feature.player.presentation.host.playerCoverShape

/**
 * The kit's glass [MiniPlayer] for [track] — the cover, the title and the subtitle, play or pause
 * (stop on a station), next and the progress line — with the round Remix wand beside it.
 *
 * The kit draws its own cover and has no way to make it a shared element, so the same cover is
 * drawn over it, in the same place, with [coverModifier]: that copy is what grows into the full
 * player's cover.
 *
 * @param progress read only while drawing the progress line, from 0 to 1.
 * @param onNext `null` when there is nothing to skip to: the kit's mini player has no disabled
 *   state for it, so the button goes.
 * @param onRemix `null` hides the wand, as for a station.
 * @param pillModifier for the pill alone, such as the drag that opens the full player.
 */
@Composable
fun MiniPlayerPill(
    track: NowPlayingUi,
    isPlaying: Boolean,
    isStation: Boolean,
    progress: () -> Float,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)?,
    onRemix: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pillModifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(pillModifier.weight(1f)) {
            MiniPlayer(
                title = track.title,
                subtitle = track.subtitle,
                coverSeed = track.coverSeed,
                isPlaying = isPlaying,
                progress = progress,
                onClick = onOpen,
                onPlayPause = onPlayPause,
                onNext = onNext,
                live = isStation,
            )
            // Where the kit places its cover: the pill's start padding, centered vertically.
            Cover(
                seed = track.coverSeed,
                size = dimens.size.cover.xs,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = dimens.spacing.xs)
                    .then(coverModifier),
                shape = playerCoverShape(),
            )
        }
        if (onRemix != null) {
            GlassIconButton(
                icon = StudioIcons.AutoFixHigh,
                contentDescription = stringResource(R.string.player_remix),
                onClick = onRemix,
                size = dimens.size.component.miniPlayer,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerPillPreview() {
    PreviewScreen {
        Column(Modifier.padding(StudioTheme.dimens.spacing.m)) {
            MiniPlayerPill(
                track = NowPlayingUi(TrackId("glass-harbor"), "Glass Harbor", "Mira Vale", "Glass Harbor"),
                isPlaying = true,
                isStation = false,
                progress = { 0.4f },
                onOpen = {},
                onPlayPause = {},
                onNext = {},
                onRemix = {},
            )
            MiniPlayerPill(
                track = NowPlayingUi(TrackId("static-bloom"), "Late Night Lo-fi", "Static Bloom", "Static Bloom"),
                isPlaying = true,
                isStation = true,
                progress = { 0f },
                onOpen = {},
                onPlayPause = {},
                onNext = null,
                onRemix = null,
            )
        }
    }
}
