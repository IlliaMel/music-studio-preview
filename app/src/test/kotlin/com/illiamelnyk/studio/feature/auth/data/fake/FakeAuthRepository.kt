package com.illiamelnyk.studio.feature.auth.data.fake

import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * "Google" answers after a second with the demo account, signed in through [sessions] the way the
 * backend's development tokens sign in `dev:illia`.
 *
 * Temporary: replaced by the backend-backed implementation (Credential Manager and Firebase Auth).
 */
class FakeAuthRepository(private val sessions: FakeSessionRepository) : AuthRepository {
    override val requiresGoogleCredential: Boolean = false
    override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> =
        Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.UnsupportedMode)
    override suspend fun signInDemo(): Outcome<Unit> {
        sessions.signIn(
            name = "demo",
            method = SignInMethod.Google,
            suggestedDisplayName = "Demo listener",
            useProviderProfile = true,
        )
        return Outcome.Success(Unit)
    }

    override suspend fun signInWithGoogle(): Outcome<Unit> {
        delay(1.seconds)
        sessions.signIn(
            name = "illia",
            method = SignInMethod.Google,
            suggestedDisplayName = "Illia Melnyk",
            useProviderProfile = true,
        )
        return Outcome.Success(Unit)
    }
}
