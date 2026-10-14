package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.feature.create.presentation.tool.components.ToolAction
import com.illiamelnyk.studio.feature.create.presentation.tool.components.ToolHero

/** A tool's screen: lays out its sections and turns their callbacks into intents. */
@Composable
fun ToolScreen(
    state: ToolState,
    onIntent: (ToolIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        BackTopBar(onBack = { onIntent(ToolIntent.BackClicked) })
        ToolHero(
            tool = state.tool,
            description = state.description,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
        ToolAction(
            needsRecording = state.needsRecording,
            isSubmitting = state.isSubmitting,
            notice = state.notice,
            onUseClick = { onIntent(ToolIntent.UseToolClicked) },
        )
    }
}

@PreviewLightDark
@Composable
private fun ToolScreenPreview(@PreviewParameter(ToolPreviewData::class) state: ToolState) {
    PreviewScreen { ToolScreen(state = state, onIntent = {}) }
}
