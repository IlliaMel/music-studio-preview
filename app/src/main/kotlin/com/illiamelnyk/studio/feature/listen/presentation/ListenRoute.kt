package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Listen tab, wired to its ViewModel.
 *
 * @param onOpenCollection navigates to a collection by its id.
 * @param contentPadding the space the app's bars and the system bars take over the content.
 */
@Composable
fun ListenRoute(
    onOpenCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ListenViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ListenEffect.OpenCollection -> onOpenCollection(effect.id)
        }
    }
    ListenScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, contentPadding = contentPadding)
}
