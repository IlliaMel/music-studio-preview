package com.illiamelnyk.studio.feature.create.presentation.tools

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

enum class ToolsFilter(@param:StringRes val label: Int) {
    All(R.string.create_tools_all),
    Featured(R.string.create_tools_featured),
    Studio(R.string.create_tools_studio),
}

@Immutable
data class ToolsState(
    val filter: ToolsFilter = ToolsFilter.All,
    val tools: ImmutableList<ToolUi> = Tool.entries.map(Tool::toUi).toImmutableList(),
)

sealed interface ToolsIntent {
    data object BackClicked : ToolsIntent
    data class FilterSelected(val filter: ToolsFilter) : ToolsIntent
    data class ToolClicked(val tool: Tool) : ToolsIntent
}

sealed interface ToolsEffect {
    data object Close : ToolsEffect
    data class OpenTool(val tool: Tool) : ToolsEffect
}
