package com.illiamelnyk.studio.feature.create.presentation.tool.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.toUi

/** The tool's cover, the "studio" badge, its name and what it does. */
@Composable
fun ToolHero(tool: ToolUi, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(seed = tool.coverSeed, size = dimens.size.cover.l)
        StudioBadge(stringResource(R.string.tool_badge))
        StudioText(stringResource(tool.title), StudioTheme.typography.titleLarge, textAlign = TextAlign.Center)
        StudioText(
            text = stringResource(tool.description),
            style = StudioTheme.typography.bodyMedium,
            color = StudioTheme.colors.content.secondary,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun ToolHeroPreview() {
    PreviewScreen { ToolHero(tool = Tool.Genreshift.toUi()) }
}
