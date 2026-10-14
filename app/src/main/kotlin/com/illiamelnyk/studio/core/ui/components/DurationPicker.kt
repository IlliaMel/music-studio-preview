package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * One duration out of a few, as a row of filter chips that wraps when it runs out of room.
 *
 * @param label the text of an option, such as "1:30".
 */
@Composable
fun <T> DurationPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    val spacing = StudioTheme.dimens.spacing
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        options.forEach { option ->
            StudioFilterChip(text = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

@PreviewLightDark
@Composable
private fun DurationPickerPreview() {
    PreviewSurface {
        var seconds by remember { mutableIntStateOf(60) }
        DurationPicker(listOf(30, 45, 60, 90, 180), selected = seconds, onSelect = { seconds = it }) {
            "%d:%02d".format(it / 60, it % 60)
        }
    }
}
