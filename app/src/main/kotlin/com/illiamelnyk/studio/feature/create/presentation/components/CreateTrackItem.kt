package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData
import com.illiamelnyk.studio.feature.create.presentation.CreateTrackUi
import com.illiamelnyk.studio.feature.create.presentation.TrackMenuAction

/**
 * One of the user's tracks: its row with the menu of a finished track, how far composing is, or,
 * under a failed one, what can be done about it: the fix of a refused prompt, a retry, or deleting it.
 */
@Composable
fun CreateTrackItem(
    track: CreateTrackUi,
    isPlaying: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onRetryClick: () -> Unit,
    onFixClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMenuAction: (TrackMenuAction) -> Unit,
    onMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    inGroup: Boolean = false,
) {
    val dimens = StudioTheme.dimens
    Column(modifier.padding(horizontal = if (inGroup) dimens.spacing.none else dimens.layout.screenPadding)) {
        track.dayLabel?.takeUnless { inGroup }?.let {
            StudioText(it.asString(), StudioTheme.typography.labelLarge, Modifier.padding(vertical = dimens.spacing.s))
        }
        // The menu anchors to the row and opens under its end, by the "more" button.
        Box {
            if (track.row.status == TrackUi.Status.Generating) {
                GeneratingTrackRow(track.row, track.progress)
            } else {
                TrackUiRow(
                    track = track.row,
                    onClick = onClick,
                    isPlaying = isPlaying,
                    onMore = onMoreClick,
                    titleSuffix = track.variation?.let { number ->
                        {
                            StudioText(
                                stringResource(R.string.create_variation_number, number),
                                StudioTheme.typography.bodySmall,
                                color = StudioTheme.colors.content.tertiary,
                            )
                        }
                    },
                )
            }
            TrackMenu(
                expanded = menuExpanded,
                onAction = onMenuAction,
                onDismissRequest = onMenuDismiss,
                canUseAsReference = track.canUseAsReference,
                canUsePrompt = track.canUsePrompt,
                canPlayNext = !isPlaying,
            )
        }
        if (track.row.status is TrackUi.Status.Failed) {
            FailedTrackActions(
                fix = track.fix,
                canRetry = track.canRetry,
                onFixClick = onFixClick,
                onRetryClick = onRetryClick,
                onDeleteClick = onDeleteClick,
                // Lined up with the row's text, after the cover.
                modifier = Modifier.padding(start = dimens.size.cover.s + dimens.spacing.s, bottom = dimens.spacing.xs),
            )
        }
    }
}

private class TrackItemPreviewData : PreviewParameterProvider<CreateTrackUi> {
    override val values = CreatePreviewData.tracks.asSequence()
}

@PreviewLightDark
@Composable
private fun CreateTrackItemPreview(@PreviewParameter(TrackItemPreviewData::class) track: CreateTrackUi) {
    PreviewScreen {
        CreateTrackItem(
            track = track,
            isPlaying = false,
            menuExpanded = false,
            onClick = {},
            onMoreClick = {},
            onRetryClick = {},
            onFixClick = {},
            onDeleteClick = {},
            onMenuAction = {},
            onMenuDismiss = {},
        )
    }
}
