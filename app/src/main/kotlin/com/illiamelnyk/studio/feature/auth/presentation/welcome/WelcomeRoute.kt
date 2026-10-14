package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * The welcome screen, wired to its ViewModel.
 *
 * @param contentPadding the space the system bars take over the content.
 */
@Composable
fun WelcomeRoute(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: WelcomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WelcomeScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, contentPadding = contentPadding)
}
