package com.illiamelnyk.studio.feature.listen.presentation.collection

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.core.presentation.playlist.PlaylistPickerRoute
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
    onOpenPlayer: () -> Unit = {},
    onOpenCollection: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CollectionViewModel = koinViewModel { parametersOf(collectionId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            CollectionEffect.Close -> onClose()
            CollectionEffect.OpenPlayer -> onOpenPlayer()
            is CollectionEffect.OpenCollection -> onOpenCollection(effect.id)
        }
    }
    CollectionScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
    )
    state.pickerTrack?.let { track -> PlaylistPickerRoute(track, onDismiss = { viewModel.onIntent(CollectionIntent.DismissPicker) }) }
}
