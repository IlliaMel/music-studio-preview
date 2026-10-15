package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.presentation.withoutTop
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerAwarePadding
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerLayout

/**
 * The Create tab: the feed with the composer pinned under it, above the keyboard, and
 * sheets over both. The parts map their sections' callbacks to intents.
 *
 * The screen consumes [contentPadding] (see the shell), so the composer's `imePadding()` adds only
 * the part of the keyboard that rises above the bars.
 */
@Composable
fun CreateScreen(
    state: CreateState,
    onIntent: (CreateIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
) {
    val gap = StudioTheme.dimens.spacing.xs
    Box(modifier.fillMaxSize()) {
        ComposerLayout(
            composer = {
                CreateComposer(state.composer, onIntent, Modifier.padding(contentPadding.withoutTop()).imePadding())
            },
        ) { space ->
            CreateFeed(
                state = state.feed,
                onIntent = onIntent,
                listState = listState,
                contentPadding = remember(contentPadding, space, gap) {
                    ComposerAwarePadding(contentPadding, space, gap)
                },
            )
        }
        CreateOverlays(state, onIntent)
    }
}

@PreviewLightDark
@Composable
private fun CreateScreenPreview(@PreviewParameter(CreatePreviewData::class) state: CreateState) {
    PreviewScreen { CreateScreen(state = state, onIntent = {}) }
}
