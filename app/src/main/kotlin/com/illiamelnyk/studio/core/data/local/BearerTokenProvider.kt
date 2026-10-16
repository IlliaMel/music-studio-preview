package com.illiamelnyk.studio.core.data.local

/** An adapter must obtain a Firebase ID token from the signed-in client SDK, never a Google OAuth token. */
fun interface BearerTokenProvider {
    suspend fun getToken(firebaseUid: String): FirebaseBearerToken?

    suspend fun clearSession(firebaseUid: String) = Unit

    suspend fun confirmIdentity(firebaseUid: String) = Unit
}

data class FirebaseBearerToken(val firebaseUid: String, val value: String, val expiresAtMillis: Long)

internal class FirebaseIdentityMissing(val firebaseUid: String) : java.io.IOException()
internal class FirebaseReauthenticationRequired(val firebaseUid: String) : java.io.IOException()

internal object UnavailableBearerTokenProvider : BearerTokenProvider {
    override suspend fun getToken(firebaseUid: String): FirebaseBearerToken? = null
}
