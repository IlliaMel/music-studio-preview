package com.illiamelnyk.studio.feature.auth.domain

import com.illiamelnyk.studio.core.domain.Outcome

/**
 * Signing in. Once it succeeds, the session appears in the shared `SessionRepository`, which
 * handles everything after it: the profile, signing out, deleting the account.
 */
interface AuthRepository {
    /** Signs in with the Google account on the device. */
    suspend fun signInWithGoogle(): Outcome<Unit>
}
