package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText

/** Welcome: ready, signing in, and after a failed sign-in. */
class WelcomePreviewData : PreviewParameterProvider<WelcomeState> {
    override val values = sequenceOf(
        WelcomeState(),
        WelcomeState(isSigningIn = true),
        WelcomeState(notice = UiText.Resource(R.string.error_network)),
    )
}
