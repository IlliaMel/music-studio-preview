package com.illiamelnyk.studio.feature.auth.data

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class FirebaseGoogleAuthRepository(
    private val client: FirebaseClient,
    private val configured: Boolean,
    private val establishSession: suspend (String) -> Outcome<Unit>,
    private val sessionEstablished: (String) -> Boolean,
    private val retainForDeletion: (String) -> Boolean = { false },
    private val prepareSwitch: suspend (String?) -> Outcome<Unit>,
) : AuthRepository {
    override val requiresGoogleCredential: Boolean get() = configured

    override suspend fun signInWithGoogle(): Outcome<Unit> = Outcome.Failure(AppError.UnsupportedMode)

    override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> {
        if (!configured) return Outcome.Failure(AppError.UnsupportedMode)
        if (idToken.isBlank() || idToken.length > 16384) return Outcome.Failure(AppError.Unauthorized)
        var uid: String? = null
        var accepted = false
        try {
            val currentUid = withContext(Dispatchers.IO) { client.currentUid }
            val prepared = prepareSwitch(currentUid)
            if (prepared is Outcome.Failure) return prepared
            uid = client.signIn(idToken)
            val result = establishSession(uid)
            accepted = result is Outcome.Success
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return Outcome.Failure(AppError.Network)
        } catch (_: Exception) {
            return Outcome.Failure(AppError.Unauthorized)
        } finally {
            val signedIn = uid
            if (signedIn != null && !accepted && !sessionEstablished(signedIn)) {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (!retainForDeletion(signedIn)) client.clear(signedIn)
                }
            }
        }
    }
}
