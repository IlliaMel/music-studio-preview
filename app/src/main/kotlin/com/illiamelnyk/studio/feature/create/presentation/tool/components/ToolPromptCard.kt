package com.illiamelnyk.studio.feature.create.presentation.tool.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.PromptPart
import com.illiamelnyk.studio.core.ui.components.PromptTemplate
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.toUi
import com.illiamelnyk.studio.feature.create.presentation.tool.toParts
import kotlinx.collections.immutable.ImmutableList

/** "New track · <tool>": the tool's prompt, each slot underlined and opening its options. */
@Composable
fun ToolPromptCard(
    tool: ToolUi,
    template: ImmutableList<PromptPart>,
    onSlotClick: (slotId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PromptTemplate(
        kind = stringResource(R.string.tool_new_track),
        name = stringResource(tool.title),
        imageSeed = tool.coverSeed,
        parts = template,
        onSlotClick = onSlotClick,
        modifier = modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding),
    )
}

@PreviewLightDark
@Composable
private fun ToolPromptCardPreview() {
    PreviewScreen {
        ToolPromptCard(
            tool = Tool.Genreshift.toUi(),
            template = Tool.Genreshift.template.toParts(mapOf("to" to "Synthwave")),
            onSlotClick = {},
        )
    }
}
