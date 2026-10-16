package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class WelcomeViewModel(private val auth: AuthRepository) :
    MviViewModel<WelcomeState, WelcomeIntent, WelcomeEffect>(WelcomeState()) {

    private var awaitingCredential: Long? = null
    private var requestSequence = 0L

    override fun onIntent(intent: WelcomeIntent) {
        when (intent) {
            WelcomeIntent.ContinueWithGoogleClicked -> {
                if (auth.requiresGoogleCredential) {
                    if (!state.value.isSigningIn) {
                        val request = ++requestSequence
                        awaitingCredential = request
                        setState { copy(isSigningIn = true, notice = null) }
                        sendEffect(WelcomeEffect.RequestGoogleCredential(request))
                    }
                } else {
                    signIn(demo = false)
                }
            }

            WelcomeIntent.TryDemoClicked -> signIn(demo = true)

            is WelcomeIntent.GoogleCredentialReceived -> {
                if (awaitingCredential == intent.requestId) {
                    awaitingCredential = null
                    completeSignIn { auth.signInWithGoogleToken(intent.idToken) }
                }
            }

            is WelcomeIntent.GoogleCredentialCancelled -> {
                if (awaitingCredential == intent.requestId) {
                    awaitingCredential = null
                    setState { copy(isSigningIn = false, notice = null) }
                }
            }

            is WelcomeIntent.GoogleCredentialFailed -> {
                if (awaitingCredential == intent.requestId) {
                    awaitingCredential = null
                    setState { copy(isSigningIn = false, notice = intent.error.toUiText()) }
                }
            }
        }
    }

    private fun signIn(demo: Boolean) {
        if (state.value.isSigningIn) return
        setState { copy(isSigningIn = true, notice = null) }
        completeSignIn { if (demo) auth.signInDemo() else auth.signInWithGoogle() }
    }

    private fun completeSignIn(action: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            val result = try {
                action()
            } catch (cancelled: CancellationException) {
                setState { copy(isSigningIn = false, notice = null) }
                throw cancelled
            }
            // On success the app leaves this screen; until then the button keeps its spinner.
            if (result is Outcome.Failure) setState { copy(isSigningIn = false, notice = result.error.toUiText()) }
        }
    }
}
