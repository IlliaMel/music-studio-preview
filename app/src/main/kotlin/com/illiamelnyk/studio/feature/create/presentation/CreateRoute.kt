package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.feature.create.domain.Tool
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Create tab, wired to its ViewModel.
 *
 * @param onOpenTool navigates to a tool's screen.
 * @param contentPadding the space the app's bars and the system bars take over the content.
 */
@Composable
fun CreateRoute(
    onOpenTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CreateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is CreateEffect.OpenTool -> onOpenTool(effect.tool)
        }
    }
    CreateScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, contentPadding = contentPadding)
}
