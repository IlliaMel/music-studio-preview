package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import kotlinx.coroutines.launch

class WelcomeViewModel(private val auth: AuthRepository) :
    MviViewModel<WelcomeState, WelcomeIntent, Nothing>(WelcomeState()) {

    override fun onIntent(intent: WelcomeIntent) = when (intent) {
        WelcomeIntent.ContinueWithGoogleClicked -> signIn()
    }

    private fun signIn() {
        if (state.value.isSigningIn) return
        setState { copy(isSigningIn = true, notice = null) }
        viewModelScope.launch {
            val result = auth.signInWithGoogle()
            // On success the app leaves this screen; until then the button keeps its spinner.
            if (result is Outcome.Failure) setState { copy(isSigningIn = false, notice = result.error.toUiText()) }
        }
    }
}
