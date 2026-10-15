package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonSheet
import com.illiamelnyk.studio.feature.create.presentation.tool.components.AddRecordingRow
import com.illiamelnyk.studio.feature.create.presentation.tool.components.SlotSheet
import com.illiamelnyk.studio.feature.create.presentation.tool.components.ToolAction
import com.illiamelnyk.studio.feature.create.presentation.tool.components.ToolHero
import com.illiamelnyk.studio.feature.create.presentation.tool.components.ToolLength
import com.illiamelnyk.studio.feature.create.presentation.tool.components.ToolPromptCard

/** A tool's screen: lays out its sections, with "Use tool" pinned at the bottom, and maps their callbacks to intents. */
@Composable
fun ToolScreen(
    state: ToolState,
    onIntent: (ToolIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val dismiss = { onIntent(ToolIntent.SheetDismissed) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            BackTopBar(onBack = { onIntent(ToolIntent.BackClicked) }, title = stringResource(state.tool.title))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.l),
            ) {
                ToolHero(state.tool)
                ToolPromptCard(state.tool, state.template, onSlotClick = { onIntent(ToolIntent.SlotClicked(it)) })
                ToolLength(state.durations, state.duration, onSelect = { onIntent(ToolIntent.DurationSelected(it)) })
                state.recording?.let { AddRecordingRow(it, onClick = { onIntent(ToolIntent.AddRecordingClicked) }) }
                state.referenceAudio?.let {
                    StudioText(
                        stringResource(R.string.tool_reference_ready),
                        StudioTheme.typography.bodySmall,
                        Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding),
                    )
                }
            }
            ToolAction(state.canUse, state.hint, state.isSubmitting, onUseClick = {
                onIntent(ToolIntent.UseToolClicked)
            })
        }
        SlotSheet(state.slotSheet, onSelect = {
            onIntent(ToolIntent.SlotValueSelected(it))
        }, onDismissRequest = dismiss)
        ComingSoonSheet(state.comingSoon, onWantClick = {
            onIntent(ToolIntent.WantClicked)
        }, onDismissRequest = dismiss)
    }
}

@PreviewLightDark
@Composable
private fun ToolScreenPreview(@PreviewParameter(ToolPreviewData::class) state: ToolState) {
    PreviewScreen { ToolScreen(state = state, onIntent = {}) }
}
