package com.illiamelnyk.studio.feature.auth.data

import com.illiamelnyk.studio.core.data.local.StudioAuthentication
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository

class DevelopmentAuthRepository(private val data: StudioAuthentication) : AuthRepository {
    override val requiresGoogleCredential: Boolean = false
    override suspend fun signInWithGoogleToken(idToken: String): Outcome<Unit> =
        Outcome.Failure(com.illiamelnyk.studio.core.domain.AppError.UnsupportedMode)
    override suspend fun signInWithGoogle(): Outcome<Unit> = data.signInServer()
    override suspend fun signInDemo(): Outcome<Unit> = data.signInDemo()
}
