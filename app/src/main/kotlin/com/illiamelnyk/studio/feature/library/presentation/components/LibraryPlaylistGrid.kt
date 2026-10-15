package com.illiamelnyk.studio.feature.library.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.TrackCard
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.library.presentation.PlaylistUi
import kotlinx.collections.immutable.ImmutableList

@Composable
fun LibraryPlaylistGrid(playlists: ImmutableList<PlaylistUi>, onCreate: () -> Unit, onExplore: () -> Unit, onSelect: (String) -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val dimens = StudioTheme.dimens
    LazyVerticalGrid(columns = GridCells.Adaptive(dimens.size.cover.l), modifier = modifier,
        contentPadding = contentPadding + PaddingValues(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.m), horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
        item(key = "create", span = { GridItemSpan(maxLineSpan) }) {
            StudioButton(stringResource(R.string.library_create_playlist), onClick = onCreate, style = StudioButtonStyle.Text)
        }
        if (playlists.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(StudioIcons.LibraryMusic, stringResource(R.string.library_playlists), stringResource(R.string.library_playlist_empty), action = {
                StudioButton(stringResource(R.string.library_explore), onClick = onExplore)
            })
        }
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            TrackCard(playlist.name, playlist.subtitle.asString(), playlist.coverSeed, onClick = { onSelect(playlist.id) }, modifier = Modifier.animateItem())
        }
    }
}
