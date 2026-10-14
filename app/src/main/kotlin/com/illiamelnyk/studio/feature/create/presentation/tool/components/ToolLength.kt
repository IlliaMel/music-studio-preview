package com.illiamelnyk.studio.feature.create.presentation.tool.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.DurationPicker
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.trackLengthOptions
import kotlinx.collections.immutable.ImmutableList

/** "Duration" and the lengths to pick from, as chips that wrap. */
@Composable
fun ToolLength(
    durations: ImmutableList<DurationOptionUi>,
    selected: DurationOptionUi,
    onSelect: (DurationOptionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.padding(horizontal = dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        StudioText(stringResource(R.string.tool_duration), StudioTheme.typography.titleSmall)
        DurationPicker(durations, selected = selected, onSelect = onSelect) { it.label }
    }
}

@PreviewLightDark
@Composable
private fun ToolLengthPreview() {
    PreviewScreen { ToolLength(trackLengthOptions, selected = trackLengthOptions[2], onSelect = {}) }
}
