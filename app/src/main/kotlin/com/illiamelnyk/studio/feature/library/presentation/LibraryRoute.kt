package com.illiamelnyk.studio.feature.library.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
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
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            LibraryEffect.Close -> onBack?.invoke()
        }
    }
    LibraryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
        showBackButton = onBack != null,
    )
}
