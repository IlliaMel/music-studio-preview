package com.illiamelnyk.studio.feature.library.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.core.presentation.playlist.PlaylistPickerRoute
import org.koin.compose.viewmodel.koinViewModel

/**
 * Library, wired to its ViewModel.
 *
 * @param onBack set when Library is opened on top of another screen: it then has its own top bar
 *   with a back button. As a tab it has none; the app's top bar is above it.
 * @param contentPadding the space the app's bars and the system bars take over the content.
 */
@Composable
fun LibraryRoute(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit = {},
    onExplore: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.selectedPlaylistId != null && state.pickerTrack == null && !state.showCreatePlaylist) {
        viewModel.onIntent(LibraryIntent.BackClicked)
    }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            LibraryEffect.Close -> onBack?.invoke()
            LibraryEffect.OpenPlayer -> onOpenPlayer()
            LibraryEffect.Explore -> onExplore()
        }
    }
    LibraryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
        showBackButton = onBack != null,
    )
    state.pickerTrack?.let { track -> PlaylistPickerRoute(track, onDismiss = { viewModel.onIntent(LibraryIntent.DismissPicker) }) }
}
