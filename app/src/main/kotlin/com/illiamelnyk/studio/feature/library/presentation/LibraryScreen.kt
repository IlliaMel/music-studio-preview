package com.illiamelnyk.studio.feature.library.presentation

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
import com.illiamelnyk.studio.feature.library.presentation.components.LikedTracks

/**
 * Library: the liked tracks.
 *
 * @param showBackButton Library is opened on top of another screen and has its own top bar.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    showBackButton: Boolean = false,
) {
    if (showBackButton) {
        Column(modifier = modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            BackTopBar(onBack = { onIntent(LibraryIntent.BackClicked) }, title = stringResource(R.string.nav_library))
            LikedTracks(
                tracks = state.liked,
                isLoading = state.isLoading,
                playingTrackId = state.playingTrackId,
                onTrackClick = { onIntent(LibraryIntent.TrackClicked(it)) },
                contentPadding = contentPadding.withoutTop(),
            )
        }
    } else {
        LikedTracks(
            tracks = state.liked,
            isLoading = state.isLoading,
            playingTrackId = state.playingTrackId,
            onTrackClick = { onIntent(LibraryIntent.TrackClicked(it)) },
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

@PreviewLightDark
@Composable
private fun LibraryScreenPreview(@PreviewParameter(LibraryPreviewData::class) state: LibraryState) {
    PreviewScreen { LibraryScreen(state = state, onIntent = {}, showBackButton = true) }
}
