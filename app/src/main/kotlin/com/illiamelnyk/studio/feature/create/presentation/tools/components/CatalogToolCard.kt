package com.illiamelnyk.studio.feature.create.presentation.tools.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.ToolUi

@Composable
fun CatalogToolCard(tool: ToolUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Cover(tool.coverSeed, maxWidth, shape = StudioTheme.shapes.xl)
        }
        StudioText(
            stringResource(R.string.create_tools_studio),
            StudioTheme.typography.bodySmall,
            color = StudioTheme.colors.content.secondary,
        )
        StudioText(stringResource(tool.title), StudioTheme.typography.titleMedium)
        StudioText(stringResource(tool.description), StudioTheme.typography.bodyMedium, maxLines = 2)
    }
}
