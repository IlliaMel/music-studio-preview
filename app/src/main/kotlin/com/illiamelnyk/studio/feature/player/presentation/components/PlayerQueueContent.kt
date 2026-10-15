package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewData
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PlayerQueueContent(
    track: NowPlayingUi,
    upNext: ImmutableList<TrackUi>,
    shuffle: Boolean,
    repeat: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoplay: () -> Unit,
    onClear: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onLyrics: (() -> Unit)? = null,
) {
    val spacing = StudioTheme.dimens.spacing.xs
    val size = StudioTheme.dimens.size
    val minimumPinnedHeight = size.touchTarget * 3 + size.component.trackRow + spacing * 3
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxHeight < minimumPinnedHeight) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("playerQueue"),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                item("nowPlaying") { QueueNowPlaying(track, onClose, onLyrics) }
                item("modes") { QueueModes(shuffle, repeat, onShuffle, onRepeat, onAutoplay) }
                item("heading") { QueueHeading(upNext.isNotEmpty(), onClear) }
                queueTracks(upNext, onTrackClick, onMove)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                QueueNowPlaying(track, onClose, onLyrics)
                QueueModes(shuffle, repeat, onShuffle, onRepeat, onAutoplay)
                QueueHeading(upNext.isNotEmpty(), onClear)
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("playerQueue")) {
                    queueTracks(upNext, onTrackClick, onMove)
                }
            }
        }
    }
}

@Composable
private fun QueueModes(
    shuffle: Boolean,
    repeat: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoplay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs),
    ) {
        QueueToggle(
            StudioIcons.Shuffle,
            stringResource(R.string.ui_shuffle),
            shuffle,
            onShuffle,
            Modifier.weight(1f),
        )
        QueueToggle(
            StudioIcons.Repeat,
            stringResource(R.string.ui_repeat),
            repeat,
            onRepeat,
            Modifier.weight(1f),
        )
        QueueToggle(
            StudioIcons.AllInclusive,
            stringResource(R.string.player_autoplay),
            false,
            onAutoplay,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueueHeading(hasTracks: Boolean, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StudioText(
            stringResource(R.string.player_playing_next),
            StudioTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (hasTracks) {
            StudioText(
                stringResource(R.string.player_clear),
                StudioTheme.typography.labelMedium,
                modifier = Modifier.minimumTouchTarget().clickable(role = Role.Button, onClick = onClear),
            )
        }
    }
}

private fun LazyListScope.queueTracks(
    tracks: ImmutableList<TrackUi>,
    onTrackClick: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    if (tracks.isEmpty()) {
        item("empty") {
            StudioText(stringResource(R.string.player_queue_empty), StudioTheme.typography.bodyMedium)
        }
    }
    itemsIndexed(
        tracks,
        key = { _, item -> item.id.value },
        contentType = { _, _ -> "queueRow" },
    ) { index, item ->
        QueueTrackRow(
            track = item,
            index = index,
            count = tracks.size,
            onClick = { onTrackClick(index) },
            onMove = onMove,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun QueueToggle(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        style = if (selected) StudioIconButtonStyle.Primary else StudioIconButtonStyle.Surface,
        size = StudioIconButtonSize.Small,
    )
}

@Composable
private fun QueueNowPlaying(track: NowPlayingUi, onClose: () -> Unit, onLyrics: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.coverSeed, StudioTheme.dimens.size.cover.xs)
        Column(Modifier.weight(1f)) {
            StudioText(track.title, StudioTheme.typography.labelLarge, maxLines = 1)
            StudioText(
                track.subtitle,
                StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.overlay.onMediaSecondary,
                maxLines = 1,
            )
        }
        if (onLyrics != null) {
            GlassIconButton(StudioIcons.Forum, stringResource(R.string.player_lyrics), onLyrics)
        }
        GlassIconButton(
            StudioIcons.PlaylistPlay,
            stringResource(R.string.player_queue),
            onClose,
            selected = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun PlayerQueueContentPreview() {
    PlayerPreviewSurface {
        PlayerQueueContent(
            track = PlayerPreviewData.glassHarbor,
            upNext = PlayerPreviewData.upNext,
            shuffle = true, repeat = false, onShuffle = {}, onRepeat = {}, onAutoplay = {},
            onClear = {}, onTrackClick = {}, onMove = { _, _ -> }, onClose = {},
        )
    }
}
