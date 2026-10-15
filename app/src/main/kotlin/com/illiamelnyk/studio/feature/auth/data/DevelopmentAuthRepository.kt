package com.illiamelnyk.studio.feature.auth.data

import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.feature.auth.domain.AuthRepository

class DevelopmentAuthRepository(private val data: DurableStudioRepository) : AuthRepository {
    override suspend fun signInWithGoogle(): Outcome<Unit> = data.signInServer()
    override suspend fun signInDemo(): Outcome<Unit> = data.signInDemo()
}
