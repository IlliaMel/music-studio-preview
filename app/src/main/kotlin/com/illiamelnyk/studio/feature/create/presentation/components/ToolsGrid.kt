package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.SectionHeader
import com.illiamelnyk.studio.core.ui.components.ToolTile
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * "Start from a Tool" and the tools two by two. A tool that needs the user's recording is marked
 * as coming soon, and still opens to explain itself.
 *
 * A tile shows its name on one line, so when the longest name doesn't fit half the width, such as
 * with a large font, the tools stack one per row instead of being cut off.
 */
@Composable
fun ToolsGrid(
    tools: ImmutableList<ToolUi>,
    onToolClick: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    onCatalogClick: () -> Unit = {},
) {
    val dimens = StudioTheme.dimens
    val names = tools.map { stringResource(it.title) }
    val measurer = rememberTextMeasurer()
    val nameStyle = StudioTheme.typography.titleSmall
    val density = LocalDensity.current
    Column(
        modifier = modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(dimens.layout.itemGap),
    ) {
        SectionHeader(stringResource(R.string.create_tools), onTitleClick = onCatalogClick)
        BoxWithConstraints {
            // A tile's name gets what its picture, the gap after it and the end padding leave.
            val halfTileText = (maxWidth - dimens.layout.itemGap) / COLUMNS - dimens.size.cover.m - dimens.spacing.s * 2
            val columns = remember(names, nameStyle, density, halfTileText) {
                val longest = names.maxOfOrNull { measurer.measure(it, nameStyle).size.width } ?: 0
                if (longest <= with(density) { halfTileText.roundToPx() }) COLUMNS else 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
                tools.zip(names).chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.layout.itemGap)) {
                        row.forEach { (tool, name) ->
                            ToolTile(
                                name = name,
                                imageSeed = tool.coverSeed,
                                onClick = { onToolClick(tool.tool) },
                                modifier = Modifier.weight(1f),
                                comingSoon = tool.needsRecording,
                            )
                        }
                        // A last row with fewer tools keeps the tiles at their width.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private const val COLUMNS = 2

@PreviewLightDark
@Composable
private fun ToolsGridPreview() {
    PreviewScreen { ToolsGrid(tools = Tool.entries.map(Tool::toUi).toImmutableList(), onToolClick = {}) }
}

@Preview(fontScale = 1.3f)
@Composable
private fun ToolsGridLargeFontPreview() {
    PreviewScreen { ToolsGrid(tools = Tool.entries.map(Tool::toUi).toImmutableList(), onToolClick = {}) }
}
