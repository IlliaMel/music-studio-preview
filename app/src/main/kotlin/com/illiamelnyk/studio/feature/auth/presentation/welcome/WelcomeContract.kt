package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.presentation.UiText

/**
 * The first screen while nobody is signed in. Signing in needs no navigation: once the session
 * appears, the app moves on by itself.
 *
 * @param notice why the last sign-in failed.
 */
@Immutable
data class WelcomeState(val isSigningIn: Boolean = false, val notice: UiText? = null)

sealed interface WelcomeIntent {
    data object ContinueWithGoogleClicked : WelcomeIntent
}
