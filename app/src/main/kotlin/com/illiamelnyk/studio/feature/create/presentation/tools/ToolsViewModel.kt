package com.illiamelnyk.studio.feature.create.presentation.tools

import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlinx.collections.immutable.toImmutableList

class ToolsViewModel : MviViewModel<ToolsState, ToolsIntent, ToolsEffect>(ToolsState()) {
    override fun onIntent(intent: ToolsIntent) = when (intent) {
        ToolsIntent.BackClicked -> sendEffect(ToolsEffect.Close)

        is ToolsIntent.ToolClicked -> sendEffect(ToolsEffect.OpenTool(intent.tool))

        is ToolsIntent.FilterSelected -> setState {
            copy(
                filter = intent.filter,
                tools = Tool.entries.filter { intent.filter != ToolsFilter.Featured || it.featured }
                    .map(Tool::toUi).toImmutableList(),
            )
        }
    }
}
