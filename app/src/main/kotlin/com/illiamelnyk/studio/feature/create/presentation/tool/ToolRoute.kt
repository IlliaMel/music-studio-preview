package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.common.ToastHostState
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tool's screen, wired to its ViewModel.
 *
 * @param onClose leaves the screen.
 * @param contentPadding the space the system bars and the mini player take over the content.
 */
@Composable
fun ToolRoute(
    tool: Tool,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ToolViewModel = koinViewModel { parametersOf(tool) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toasts = remember { ToastHostState() }
    val scope = rememberCoroutineScope()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ToolEffect.Close -> onClose()
            is ToolEffect.ShowToast -> scope.launch { toasts.show(effect.toast) }
        }
    }
    ToolScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
        toasts = toasts,
    )
}
