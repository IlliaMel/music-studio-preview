package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** "Start from a Tool" and a row of the tools, scrolling sideways past the screen edges. */
@Composable
fun ToolsSection(tools: ImmutableList<ToolUi>, onToolClick: (Tool) -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
        StudioText(
            text = stringResource(R.string.create_tools),
            style = StudioTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = dimens.layout.screenPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = dimens.layout.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        ) {
            items(tools, key = { it.tool.name }) { tool ->
                StudioChip(
                    text = stringResource(tool.title),
                    onClick = { onToolClick(tool.tool) },
                    leadingAvatar = {
                        Cover(tool.coverSeed, dimens.size.avatar.xs, shape = StudioTheme.shapes.full)
                    },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ToolsSectionPreview() {
    PreviewScreen {
        ToolsSection(tools = Tool.entries.map(Tool::toUi).toImmutableList(), onToolClick = {})
    }
}
