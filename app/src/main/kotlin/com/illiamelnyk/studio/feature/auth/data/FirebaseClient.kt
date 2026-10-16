package com.illiamelnyk.studio.feature.auth.data

import com.illiamelnyk.studio.core.data.local.BearerTokenProvider
import com.illiamelnyk.studio.core.data.local.FirebaseBearerToken
import com.illiamelnyk.studio.core.data.local.FirebaseReauthenticationRequired

internal interface FirebaseClient {
    val currentUid: String?
    suspend fun signIn(googleIdToken: String): String
    suspend fun token(uid: String, forceRefresh: Boolean = false): FirebaseBearerToken?
    suspend fun clear(uid: String)
}

internal class FirebaseSdkTokenProvider(private val client: FirebaseClient) : BearerTokenProvider {
    override suspend fun getToken(firebaseUid: String): FirebaseBearerToken? {
        if (client.currentUid != firebaseUid) return null
        return client.token(firebaseUid)?.takeIf {
            it.firebaseUid == firebaseUid && client.currentUid == firebaseUid
        }
    }

    override suspend fun clearSession(firebaseUid: String) = client.clear(firebaseUid)

    override suspend fun confirmIdentity(firebaseUid: String) {
        if (client.currentUid != firebaseUid) throw FirebaseReauthenticationRequired(firebaseUid)
        client.token(firebaseUid, forceRefresh = true)
    }
}
