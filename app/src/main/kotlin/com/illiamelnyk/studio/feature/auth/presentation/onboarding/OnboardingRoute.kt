package com.illiamelnyk.studio.feature.auth.presentation.onboarding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * The onboarding steps, wired to their ViewModel.
 *
 * @param contentPadding the space the system bars take over the content.
 */
@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
