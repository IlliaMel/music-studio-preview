package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.presentation.withoutTop
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.collection.components.CollectionHeader

/** A collection: a back button, the header with Play and Shuffle, then the tracks. */
@Composable
fun CollectionScreen(
    state: CollectionState,
    onIntent: (CollectionIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        BackTopBar(onBack = { onIntent(CollectionIntent.BackClicked) })
        LazyColumn(contentPadding = contentPadding.withoutTop()) {
            val collection = state.collection
            if (collection != null) {
                item(key = "header", contentType = "header") {
                    CollectionHeader(
                        collection = collection,
                        onPlayClick = { onIntent(CollectionIntent.PlayClicked) },
                        onShuffleClick = { onIntent(CollectionIntent.ShuffleClicked) },
                    )
                }
            }
            items(state.tracks, key = { it.id.value }, contentType = { "track" }) { track ->
                TrackUiRow(
                    track = track,
                    onClick = { onIntent(CollectionIntent.TrackClicked(track.id)) },
                    modifier = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding),
                    isPlaying = track.id == state.playingTrackId,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CollectionScreenPreview(@PreviewParameter(CollectionPreviewData::class) state: CollectionState) {
    PreviewScreen { CollectionScreen(state = state, onIntent = {}) }
}
