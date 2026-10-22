package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.Outcome

/** Identity-provider integration boundary; UI features depend on their own AuthRepository. */
interface StudioAuthentication {
    suspend fun signInDemo(): Outcome<Unit>
    suspend fun signInServer(): Outcome<Unit>
    suspend fun signInFirebase(firebaseUid: String): Outcome<Unit>
    fun isFirebaseSessionActive(firebaseUid: String): Boolean
    fun needsFirebaseDeletionCredentials(firebaseUid: String): Boolean
    suspend fun prepareFirebaseSignIn(currentFirebaseUid: String?): Outcome<Unit>
    suspend fun completeFirebaseSignIn(firebaseUid: String): Outcome<Unit>
}
