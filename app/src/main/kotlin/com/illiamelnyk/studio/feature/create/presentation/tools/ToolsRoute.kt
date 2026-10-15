package com.illiamelnyk.studio.feature.create.presentation.tools

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.feature.create.domain.Tool
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ToolsRoute(
    onClose: () -> Unit,
    onOpenTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ToolsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) {
        when (it) {
            ToolsEffect.Close -> onClose()
            is ToolsEffect.OpenTool -> onOpenTool(it.tool)
        }
    }
    ToolsScreen(state, viewModel::onIntent, modifier, contentPadding)
}
