package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.components.Carousel
import com.illiamelnyk.studio.core.ui.components.SectionHeader
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.TrackCard
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.presentation.collection.components.CollectionActions
import com.illiamelnyk.studio.feature.listen.presentation.collection.components.CollectionHeader
import com.illiamelnyk.studio.feature.listen.presentation.collection.components.CollectionSkeleton

@Composable
fun CollectionScreen(
    state: CollectionState,
    onIntent: (CollectionIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val direction = LocalLayoutDirection.current
    val startInset = contentPadding.calculateStartPadding(direction)
    val endInset = contentPadding.calculateEndPadding(direction)
    val safeSides = Modifier.padding(start = startInset, end = endInset)
    val padding = safeSides.padding(horizontal = StudioTheme.dimens.layout.screenPadding)
    val topInset = contentPadding.calculateTopPadding()
    Box(modifier.fillMaxSize().background(StudioTheme.colors.background)) {
        LazyColumn(
            modifier = Modifier.testTag("collection-list").padding(
                top = if (state.collection == null ||
                    state.failed
                ) {
                    topInset + StudioTheme.dimens.size.component.topBar
                } else {
                    0.dp
                },
            ),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            if (state.isLoading && state.collection == null) item(key = "loading") { CollectionSkeleton() }
            if (state.failed) {
                item(key = "error") {
                    StudioButton(stringResource(R.string.listen_retry), onClick = { onIntent(CollectionIntent.Retry) })
                }
            }
            state.collection?.let { collection ->
                item(key = "header") {
                    CollectionHeader(
                        collection,
                        Modifier.testTag("collection-hero"),
                        contentPadding = PaddingValues(start = startInset, top = topInset, end = endInset),
                    )
                }
                item(key = "actions") {
                    CollectionActions(onPlay = {
                        onIntent(CollectionIntent.PlayClicked)
                    }, onShuffle = {
                        onIntent(CollectionIntent.ShuffleClicked)
                    }, enabled = state.tracks.isNotEmpty(), modifier = safeSides)
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
                    Carousel(state.related, modifier = safeSides, key = { it.id }) { collection ->
                        TrackCard(collection.title, collection.subtitle.asString(), collection.coverSeed, onClick = {
                            onIntent(CollectionIntent.RelatedClicked(collection.id))
                        })
                    }
                }
            }
        }
        BackTopBar(
            onBack = { onIntent(CollectionIntent.BackClicked) },
            modifier = Modifier.padding(start = startInset, top = topInset, end = endInset),
        )
    }
}

@PreviewLightDark
@Composable
private fun CollectionScreenPreview(@PreviewParameter(CollectionPreviewData::class) state: CollectionState) {
    PreviewScreen { CollectionScreen(state = state, onIntent = {}) }
}
