package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.components.DemoSection
import com.illiamelnyk.studio.feature.create.presentation.components.ToolsSection
import com.illiamelnyk.studio.feature.create.presentation.components.yourTracks

/** The Create tab: lays out its sections and turns their callbacks into intents. */
@Composable
fun CreateScreen(
    state: CreateState,
    onIntent: (CreateIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val dimens = StudioTheme.dimens
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = dimens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        item(key = "tools", contentType = "tools") {
            ToolsSection(tools = state.tools, onToolClick = { onIntent(CreateIntent.ToolClicked(it)) })
        }
        item(key = "demo", contentType = "demo") {
            DemoSection(
                isSubmitting = state.isSubmitting,
                notice = state.notice,
                onGenerateClick = { onIntent(CreateIntent.GenerateDemoClicked(nameAnArtist = false)) },
                onGenerateRejectedClick = { onIntent(CreateIntent.GenerateDemoClicked(nameAnArtist = true)) },
            )
        }
        yourTracks(
            tracks = state.tracks,
            isLoading = state.isLoading,
            playingTrackId = state.playingTrackId,
            onTrackClick = { onIntent(CreateIntent.TrackClicked(it)) },
            onRetryClick = { onIntent(CreateIntent.RetryClicked(it)) },
        )
    }
}

@PreviewLightDark
@Composable
private fun CreateScreenPreview(@PreviewParameter(CreatePreviewData::class) state: CreateState) {
    PreviewScreen { CreateScreen(state = state, onIntent = {}) }
}
