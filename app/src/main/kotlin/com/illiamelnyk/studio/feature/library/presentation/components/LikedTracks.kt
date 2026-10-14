package com.illiamelnyk.studio.feature.library.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** "Liked": the liked tracks, or how to get some there. Nothing while they load. */
@Composable
fun LikedTracks(
    tracks: ImmutableList<TrackUi>,
    isLoading: Boolean,
    playingTrackId: TrackId?,
    onTrackClick: (TrackId) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val dimens = StudioTheme.dimens
    if (!isLoading && tracks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = StudioIcons.LibraryMusic,
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                modifier = Modifier.padding(dimens.spacing.xxl),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = dimens.spacing.m),
    ) {
        item(key = "header", contentType = "header") {
            StudioText(
                text = stringResource(R.string.library_liked),
                style = StudioTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xs),
            )
        }
        items(tracks, key = { it.id.value }, contentType = { "track" }) { track ->
            TrackUiRow(
                track = track,
                onClick = { onTrackClick(track.id) },
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = dimens.layout.screenPadding),
                isPlaying = track.id == playingTrackId,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun LikedTracksPreview() {
    PreviewScreen {
        LikedTracks(
            tracks = persistentListOf(
                TrackUi(
                    id = TrackId("1"),
                    title = "Rainy window lo-fi",
                    subtitle = UiText.Raw("Illia Melnyk · 2:00"),
                    coverSeed = "Rainy window lo-fi",
                    status = TrackUi.Status.Ready,
                ),
            ),
            isLoading = false,
            playingTrackId = null,
            onTrackClick = {},
        )
    }
}
