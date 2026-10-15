package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.feature.auth.presentation.welcome.components.WelcomeActions
import com.illiamelnyk.studio.feature.auth.presentation.welcome.components.WelcomeCollage

/** The welcome screen: the cover collage above, the sign-in below. */
@Composable
fun WelcomeScreen(
    state: WelcomeState,
    onIntent: (WelcomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        WelcomeCollage(modifier = Modifier.weight(1f))
        WelcomeActions(
            onTryDemoClick = { onIntent(WelcomeIntent.TryDemoClicked) },
            isSigningIn = state.isSigningIn,
            notice = state.notice,
            onContinueWithGoogleClick = { onIntent(WelcomeIntent.ContinueWithGoogleClicked) },
        )
    }
}

@PreviewLightDark
@Composable
private fun WelcomeScreenPreview(@PreviewParameter(WelcomePreviewData::class) state: WelcomeState) {
    PreviewScreen { WelcomeScreen(state = state, onIntent = {}) }
}
