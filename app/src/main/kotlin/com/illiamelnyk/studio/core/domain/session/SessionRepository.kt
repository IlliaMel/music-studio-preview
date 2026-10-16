package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Who is signed in, and their profile. Signing in itself belongs to the auth feature; everything
 * after it is here, because several features need it.
 */
interface SessionRepository {
    /** The current session, or `null` while nobody is signed in. Emits again on every change. */
    val session: Flow<Session?>

    /** Changes the profile and returns the session with the server's view of it. */
    suspend fun updateProfile(update: ProfileUpdate): Outcome<Session>

    /** Deletes the account and everything in it, then signs out. */
    suspend fun deleteAccount(): Outcome<Unit>

    /** Signs out on this device; it always succeeds. */
    suspend fun signOut()

    /** Atomically signs out only if the expected owner is still current. */
    suspend fun signOutIfCurrent(expectedOwner: UserId?): Boolean
}
