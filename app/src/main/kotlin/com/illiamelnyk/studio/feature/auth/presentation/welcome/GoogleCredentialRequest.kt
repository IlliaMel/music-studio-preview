package com.illiamelnyk.studio.feature.auth.presentation.welcome

import android.app.Activity
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

internal class GoogleCredentialRequest(private val webClientId: String) {
    suspend fun request(activity: Activity): String {
        check(webClientId.isNotBlank()) { "Google authentication is not configured" }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val wrapper = MutableContextWrapper(activity)
        try {
            val response = CredentialManager.create(activity.applicationContext).getCredential(wrapper, request)
            val credential = response.credential
            require(
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            )
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        } finally {
            wrapper.baseContext = activity.applicationContext
        }
    }
}
