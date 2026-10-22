package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException

/** Only a dismissed chooser is silent; provider and device failures must explain how to retry. */
internal suspend fun requestGoogleCredential(
    requestId: Long,
    request: suspend () -> String,
    dispatch: (WelcomeIntent) -> Unit,
) {
    val result = try {
        WelcomeIntent.GoogleCredentialReceived(requestId, request())
    } catch (_: GetCredentialCancellationException) {
        WelcomeIntent.GoogleCredentialCancelled(requestId)
    } catch (cancelled: CancellationException) {
        dispatch(WelcomeIntent.GoogleCredentialCancelled(requestId))
        throw cancelled
    } catch (_: NoCredentialException) {
        WelcomeIntent.GoogleCredentialFailed(requestId, GoogleCredentialFailure.NoAccount)
    } catch (_: Exception) {
        // Never expose SDK messages: these may include implementation details or credentials.
        WelcomeIntent.GoogleCredentialFailed(requestId, GoogleCredentialFailure.Unavailable)
    }
    dispatch(result)
}
