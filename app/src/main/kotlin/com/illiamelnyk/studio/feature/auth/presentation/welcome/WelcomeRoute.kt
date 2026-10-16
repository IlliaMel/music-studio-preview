package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val credentials: GoogleCredentialRequest = koinInject()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is WelcomeEffect.RequestGoogleCredential -> scope.launch {
                val host = activity
                if (host == null) {
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialFailed(effect.requestId, AppError.UnsupportedMode))
                    return@launch
                }
                try {
                    val token = credentials.request(host)
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialReceived(effect.requestId, token))
                } catch (_: GetCredentialCancellationException) {
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialCancelled(effect.requestId))
                } catch (cancelled: CancellationException) {
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialCancelled(effect.requestId))
                    throw cancelled
                } catch (_: NoCredentialException) {
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialFailed(effect.requestId, AppError.Unauthorized))
                } catch (_: GetCredentialException) {
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialFailed(effect.requestId, AppError.Unauthorized))
                } catch (_: Exception) {
                    viewModel.onIntent(WelcomeIntent.GoogleCredentialFailed(effect.requestId, AppError.Unauthorized))
                }
            }
        }
    }
    WelcomeScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, contentPadding = contentPadding)
}
