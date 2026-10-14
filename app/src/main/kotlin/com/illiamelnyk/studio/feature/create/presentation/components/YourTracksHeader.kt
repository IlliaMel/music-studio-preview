package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.SectionHeader
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.TracksFilter

/** "Your tracks" and the switch between drafts and published tracks. */
@Composable
fun YourTracksHeader(filter: TracksFilter, onFilterSelect: (TracksFilter) -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.padding(
            start = dimens.layout.screenPadding,
            end = dimens.layout.screenPadding,
            top = dimens.spacing.m,
            bottom = dimens.spacing.xs,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        SectionHeader(stringResource(R.string.create_your_tracks))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            TracksFilter.entries.forEach { option ->
                StudioFilterChip(
                    text = stringResource(option.label),
                    selected = option == filter,
                    onClick = { onFilterSelect(option) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun YourTracksHeaderPreview() {
    PreviewScreen {
        Column {
            YourTracksHeader(filter = TracksFilter.Drafts, onFilterSelect = {})
            YourTracksHeader(filter = TracksFilter.Published, onFilterSelect = {})
        }
    }
}
