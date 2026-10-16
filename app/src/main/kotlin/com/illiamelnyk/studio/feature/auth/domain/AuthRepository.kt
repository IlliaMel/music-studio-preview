package com.illiamelnyk.studio.feature.auth.domain

import com.illiamelnyk.studio.core.domain.Outcome

/**
 * Signing in. Once it succeeds, the session appears in the shared `SessionRepository`, which
 * handles everything after it: the profile, signing out, deleting the account.
 */
interface AuthRepository {
    val requiresGoogleCredential: Boolean get() = false

    suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> =
        Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.UnsupportedMode)

    /** Signs in with the Google account on the device. */
    suspend fun signInWithGoogle(): Outcome<Unit>

    /** Uses built-in data without Firebase or a server. */
    suspend fun signInDemo(): Outcome<Unit> = signInWithGoogle()
}
