package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A collection of the catalog, wired to its ViewModel.
 *
 * @param onClose leaves the screen.
 * @param contentPadding the space the system bars and the mini player take over the content.
 */
@Composable
fun CollectionRoute(
    collectionId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CollectionViewModel = koinViewModel { parametersOf(collectionId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            CollectionEffect.Close -> onClose()
        }
    }
    CollectionScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
