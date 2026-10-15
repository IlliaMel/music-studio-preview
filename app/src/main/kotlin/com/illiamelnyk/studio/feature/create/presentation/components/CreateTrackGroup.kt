package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.track.TrackUiRow
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData
import com.illiamelnyk.studio.feature.create.presentation.CreateTrackUi

@Composable
fun CreateTrackGroup(
    tracks: List<CreateTrackUi>,
    modifier: Modifier = Modifier,
    row: @Composable (CreateTrackUi) -> Unit,
) {
    val dimens = StudioTheme.dimens
    Column(modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xxs)) {
        tracks.first().dayLabel?.let {
            StudioText(it.asString(), StudioTheme.typography.labelLarge, Modifier.padding(vertical = dimens.spacing.s))
        }
        Column(
            Modifier.clip(StudioTheme.shapes.l)
                .background(StudioTheme.colors.surface.level2)
                .padding(horizontal = dimens.spacing.xs, vertical = dimens.spacing.xxs),
        ) {
            Row(
                Modifier.padding(horizontal = dimens.spacing.xxs, vertical = dimens.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
            ) {
                StudioText(stringResource(R.string.create_variations_title), StudioTheme.typography.bodyMedium)
                StudioText(
                    pluralStringResource(R.plurals.create_variations_count, tracks.size, tracks.size),
                    StudioTheme.typography.bodyMedium,
                    color = StudioTheme.colors.content.tertiary,
                )
            }
            tracks.forEach { row(it) }
        }
    }
}

@PreviewLightDark
@Composable
private fun CreateTrackGroupPreview() {
    PreviewScreen {
        CreateTrackGroup(CreatePreviewData.tracks.take(2)) {
            TrackUiRow(it.row, onClick = {}, onMore = {})
        }
    }
}
