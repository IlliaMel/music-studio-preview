package com.illiamelnyk.studio.feature.create.presentation.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.BackTopBar
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.tools.components.CatalogToolCard

@Composable
fun ToolsScreen(
    state: ToolsState,
    onIntent: (ToolsIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val dimens = StudioTheme.dimens
    Column(modifier.fillMaxSize().padding(contentPadding)) {
        BackTopBar({ onIntent(ToolsIntent.BackClicked) }, title = stringResource(R.string.create_tools_catalog))
        StudioSegmentedControl(
            ToolsFilter.entries,
            state.filter,
            { onIntent(ToolsIntent.FilterSelected(it)) },
            Modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.s),
        ) { stringResource(it.label) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(dimens.size.cover.l * LocalDensity.current.fontScale),
            contentPadding = PaddingValues(dimens.layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap),
        ) {
            items(state.tools, key = { it.tool.name }, contentType = { "tool" }) { tool ->
                CatalogToolCard(tool, { onIntent(ToolsIntent.ToolClicked(tool.tool)) })
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ToolsScreenPreview(@PreviewParameter(ToolsPreviewData::class) state: ToolsState) {
    PreviewScreen { ToolsScreen(state, {}) }
}
