package com.illiamelnyk.studio.feature.library.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.components.CollectionHeader
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.library.presentation.PlaylistUi
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylists(
    playlists: ImmutableList<PlaylistUi>,
    selected: PlaylistUi?,
    playingTrackId: TrackId?,
    onCreate: () -> Unit,
    onExplore: () -> Unit,
    onSelect: (String?) -> Unit,
    onTrackClick: (TrackId) -> Unit,
    onTrackMore: (TrackId) -> Unit,
    onPlay: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (selected == null) {
        LibraryPlaylistGrid(playlists, onCreate, onExplore, onSelect = {
            onSelect(it)
        }, modifier = modifier, contentPadding = contentPadding)
        return
    }
    val padding = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding)
    LazyColumn(modifier, contentPadding = contentPadding) {
        item(key = "header") {
            CollectionHeader(
                selected.name,
                selected.subtitle.asString(),
                selected.coverSeed,
                artworkSize =
                    StudioTheme.dimens.size.cover.l + StudioTheme.dimens.spacing.giant,
            )
        }
        stickyHeader(key = "actions") {
            Row(
                Modifier.fillMaxWidth().background(StudioTheme.colors.surface.level1).padding(StudioTheme.dimens.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs, Alignment.CenterHorizontally),
            ) {
                StudioButton(stringResource(R.string.collection_play), onClick = {
                    onPlay(false)
                }, icon = StudioIcons.PlayArrowFilled, enabled = selected.tracks.isNotEmpty(), style = StudioButtonStyle.Secondary)
                StudioButton(stringResource(R.string.collection_shuffle), onClick = {
                    onPlay(true)
                }, icon = StudioIcons.Shuffle, enabled = selected.tracks.isNotEmpty(), style = StudioButtonStyle.Secondary)
                StudioButton(stringResource(R.string.listen_close), onClick = {
                    onSelect(null)
                }, style = StudioButtonStyle.Text)
            }
        }
        if (selected.tracks.isEmpty()) {
            item(key = "empty-tracks") {
                StudioText(stringResource(R.string.library_empty_message), StudioTheme.typography.bodyMedium, padding)
                StudioButton(stringResource(R.string.library_explore), onClick = onExplore, modifier = padding)
            }
        }
        items(selected.tracks, key = { it.id.value }, contentType = { "track" }) { track ->
            TrackUiRow(
                track,
                onClick = {
                    onTrackClick(track.id)
                },
                modifier = Modifier.animateItem().then(padding),
                isPlaying =
                    track.id == playingTrackId,
                onMore = { onTrackMore(track.id) },
            )
        }
    }
}
