package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * "Your tracks": newest first, the playing one marked, failed ones with a retry button. The rows
 * are items of the screen's list, so a long history scrolls lazily.
 */
fun LazyListScope.yourTracks(
    tracks: ImmutableList<TrackUi>,
    isLoading: Boolean,
    playingTrackId: TrackId?,
    onTrackClick: (TrackId) -> Unit,
    onRetryClick: (TrackId) -> Unit,
) {
    item(key = "tracksHeader", contentType = "header") { YourTracksHeader() }
    when {
        isLoading -> items(count = SKELETON_ROWS, key = { "skeleton$it" }, contentType = { "skeleton" }) {
            TrackRowSkeleton()
        }

        tracks.isEmpty() -> item(key = "empty", contentType = "empty") {
            EmptyState(
                icon = StudioIcons.AutoAwesome,
                title = stringResource(R.string.create_empty_title),
                message = stringResource(R.string.create_empty_message),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(StudioTheme.dimens.spacing.xxl),
            )
        }

        else -> items(tracks, key = { it.id.value }, contentType = { "track" }) { track ->
            TrackUiRow(
                track = track,
                onClick = { onTrackClick(track.id) },
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = StudioTheme.dimens.layout.screenPadding),
                isPlaying = track.id == playingTrackId,
                onRetry = { onRetryClick(track.id) },
            )
        }
    }
}

@Composable
private fun YourTracksHeader(modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    StudioText(
        text = stringResource(R.string.create_your_tracks),
        style = StudioTheme.typography.titleMedium,
        modifier = modifier.padding(
            start = dimens.layout.screenPadding,
            end = dimens.layout.screenPadding,
            top = dimens.spacing.m,
        ),
    )
}

/** The outline of a track row while the list loads. */
@Composable
private fun TrackRowSkeleton(modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.trackRow)
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.size.cover.s).skeleton(visible = true, shape = StudioTheme.shapes.s))
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            Box(Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH).height(dimens.size.icon.s).skeleton(visible = true))
            Box(Modifier.fillMaxWidth(SKELETON_SUBTITLE_WIDTH).height(dimens.spacing.s).skeleton(visible = true))
        }
    }
}

private const val SKELETON_ROWS = 3
private const val SKELETON_TITLE_WIDTH = 0.6f
private const val SKELETON_SUBTITLE_WIDTH = 0.35f
