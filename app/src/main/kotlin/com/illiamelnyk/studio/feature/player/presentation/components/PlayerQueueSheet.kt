package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewData
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import kotlinx.collections.immutable.ImmutableList

/** Upcoming tracks, with queue playback options and a clear action. */
@Composable
fun PlayerQueueSheet(
    visible: Boolean,
    upNext: ImmutableList<TrackUi>,
    shuffle: Boolean,
    repeat: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoplay: () -> Unit,
    onClear: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioBottomSheet(visible = visible, onDismissRequest = onDismissRequest, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
            StudioFilterChip(stringResource(R.string.ui_shuffle), shuffle, onShuffle)
            StudioFilterChip(stringResource(R.string.ui_repeat), repeat, onRepeat)
            StudioFilterChip(stringResource(R.string.player_autoplay), false, onAutoplay)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StudioText(
                stringResource(R.string.player_playing_next),
                StudioTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (upNext.isNotEmpty()) {
                StudioText(
                    stringResource(R.string.player_clear),
                    StudioTheme.typography.labelLarge,
                    modifier = Modifier.minimumTouchTarget().clickable(role = Role.Button, onClick = onClear),
                )
            }
        }
        if (upNext.isEmpty()) StudioText(stringResource(R.string.player_queue_empty), StudioTheme.typography.bodyMedium)
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(upNext, key = { index, track ->
                "$index:${track.id.value}"
            }, contentType = { _, _ -> "queueRow" }) { index, track ->
                TrackUiRow(track = track, onClick = { onTrackClick(index) })
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerQueueSheetPreview() {
    PlayerPreviewSurface {
        Box(Modifier.fillMaxSize()) {
            PlayerQueueSheet(
                visible = true,
                upNext = PlayerPreviewData.upNext,
                shuffle = true,
                repeat = false,
                onShuffle = {},
                onRepeat = {},
                onAutoplay = {},
                onClear = {},
                onTrackClick = {},
                onDismissRequest = {},
            )
        }
    }
}
