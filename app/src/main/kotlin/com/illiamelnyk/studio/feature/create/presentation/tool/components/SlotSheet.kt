package com.illiamelnyk.studio.feature.create.presentation.tool.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.OverlaySheet
import com.illiamelnyk.studio.core.ui.components.StudioFilterChip
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.common.rememberLatest
import com.illiamelnyk.studio.feature.create.presentation.tool.SlotSheetUi
import kotlinx.collections.immutable.persistentListOf

/** The options of a slot of the prompt, open while [slot] isn't `null`; picking one fills the slot. */
@Composable
fun SlotSheet(
    slot: SlotSheetUi?,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLatest(slot)
    OverlaySheet(visible = slot != null, onDismissRequest = onDismissRequest, modifier = modifier) {
        if (shown != null) SlotOptions(shown, onSelect)
    }
}

@Composable
private fun SlotOptions(slot: SlotSheetUi, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = StudioTheme.dimens.spacing
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        StudioText(slot.label, StudioTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            slot.options.forEach { option ->
                StudioFilterChip(text = option, selected = option == slot.selected, onClick = { onSelect(option) })
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SlotOptionsPreview() {
    PreviewScreen {
        SlotOptions(
            slot = SlotSheetUi(
                slotId = "to",
                label = "New genre",
                options = persistentListOf("Drum and bass", "Synthwave", "Bossa nova", "Orchestral", "Afrobeats"),
                selected = "Synthwave",
            ),
            onSelect = {},
            modifier = Modifier.padding(StudioTheme.dimens.spacing.m),
        )
    }
}
