package com.illiamelnyk.studio.feature.library.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.withoutTop
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.library.presentation.components.CreatePlaylistSheet
import com.illiamelnyk.studio.feature.library.presentation.components.LibraryPlaylists
import com.illiamelnyk.studio.feature.library.presentation.components.LikedTracks

@Composable
fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    showBackButton: Boolean = false,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            if (showBackButton) {
                BackTopBar(onBack = {
                    onIntent(LibraryIntent.BackClicked)
                }, title = stringResource(R.string.nav_library))
            }
            StudioSegmentedControl(LibraryTab.entries, state.tab, onSelect = {
                onIntent(LibraryIntent.TabSelected(it))
            }, modifier = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding)) {
                stringResource(if (it == LibraryTab.Liked) R.string.library_liked else R.string.library_playlists)
            }
            if (state.tab == LibraryTab.Liked) {
                LikedTracks(state.liked, state.isLoading, state.playingTrackId, onTrackClick = {
                    onIntent(LibraryIntent.TrackClicked(it))
                }, onTrackMore = {
                    onIntent(LibraryIntent.TrackMoreClicked(it))
                }, onExplore = { onIntent(LibraryIntent.ExploreClicked) }, contentPadding = contentPadding.withoutTop())
            } else {
                LibraryPlaylists(
                    state.playlists,
                    state.playlists.find {
                        it.id == state.selectedPlaylistId
                    },
                    state.playingTrackId,
                    onCreate = {
                        onIntent(LibraryIntent.NewPlaylist)
                    }, onExplore = { onIntent(LibraryIntent.ExploreClicked) },
                    onSelect = {
                        onIntent(LibraryIntent.PlaylistSelected(it))
                    }, onTrackClick = { onIntent(LibraryIntent.TrackClicked(it)) },
                    onTrackMore = {
                        onIntent(LibraryIntent.TrackMoreClicked(it))
                    }, onPlay = {
                        onIntent(LibraryIntent.PlayPlaylist(it))
                    }, contentPadding = contentPadding.withoutTop(),
                )
            }
        }
        CreatePlaylistSheet(
            state.showCreatePlaylist,
            state.playlistName,
            state.nameInvalid,
            state.failed,
            state.isBusy,
            onNameChanged = {
                onIntent(LibraryIntent.PlaylistNameChanged(it))
            },
            onCreate = {
                onIntent(LibraryIntent.CreatePlaylist)
            },
            onDismiss = { onIntent(LibraryIntent.DismissCreate) },
        )
    }
}

@PreviewLightDark
@Composable
private fun LibraryScreenPreview(@PreviewParameter(LibraryPreviewData::class) state: LibraryState) {
    PreviewScreen { LibraryScreen(state = state, onIntent = {}, showBackButton = true) }
}
