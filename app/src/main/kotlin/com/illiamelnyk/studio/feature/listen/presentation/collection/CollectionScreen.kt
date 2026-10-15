package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.presentation.withoutTop
import com.illiamelnyk.studio.core.ui.components.Carousel
import com.illiamelnyk.studio.core.ui.components.SectionHeader
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioLinearProgress
import com.illiamelnyk.studio.core.ui.components.TrackCard
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.collection.components.CollectionActions
import com.illiamelnyk.studio.feature.listen.presentation.collection.components.CollectionHeader

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionScreen(
    state: CollectionState,
    onIntent: (CollectionIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val padding = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding)
    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        BackTopBar(onBack = { onIntent(CollectionIntent.BackClicked) })
        LazyColumn(contentPadding = contentPadding.withoutTop()) {
            if (state.isLoading) item(key = "loading") { StudioLinearProgress() }
            if (state.failed) {
                item(key = "error") {
                    StudioButton(stringResource(R.string.listen_retry), onClick = { onIntent(CollectionIntent.Retry) })
                }
            }
            state.collection?.let { collection ->
                item(key = "header") { CollectionHeader(collection) }
                stickyHeader(key = "actions") {
                    CollectionActions(onPlay = {
                        onIntent(CollectionIntent.PlayClicked)
                    }, onShuffle = { onIntent(CollectionIntent.ShuffleClicked) }, enabled = state.tracks.isNotEmpty())
                }
            }
            items(state.tracks, key = { it.id.value }, contentType = { "track" }) { track ->
                TrackUiRow(
                    track,
                    onClick = {
                        onIntent(CollectionIntent.TrackClicked(track.id))
                    },
                    modifier = Modifier.animateItem().then(padding),
                    isPlaying =
                        track.id == state.playingTrackId,
                    onMore = {
                        onIntent(CollectionIntent.TrackMoreClicked(track.id))
                    },
                    badges = {
                        StudioBadge(state.versions[track.id]?.asString() ?: stringResource(R.string.library_original))
                    },
                )
            }
            if (state.related.isNotEmpty()) {
                item(key = "related") {
                    SectionHeader(
                        stringResource(R.string.listen_related),
                        padding.padding(vertical = StudioTheme.dimens.spacing.s),
                    )
                    Carousel(state.related, key = { it.id }) { collection ->
                        TrackCard(collection.title, collection.subtitle.asString(), collection.coverSeed, onClick = {
                            onIntent(CollectionIntent.RelatedClicked(collection.id))
                        })
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CollectionScreenPreview(@PreviewParameter(CollectionPreviewData::class) state: CollectionState) {
    PreviewScreen { CollectionScreen(state = state, onIntent = {}) }
}
