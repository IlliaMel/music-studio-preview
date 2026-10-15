package com.illiamelnyk.studio.feature.create.presentation.tools

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlinx.collections.immutable.toImmutableList

class ToolsPreviewData : PreviewParameterProvider<ToolsState> {
    override val values = ToolsFilter.entries.map { filter ->
        ToolsState(
            filter,
            Tool.entries.filter { filter != ToolsFilter.Featured || it.featured }.map(Tool::toUi).toImmutableList(),
        )
    }.asSequence()
}
