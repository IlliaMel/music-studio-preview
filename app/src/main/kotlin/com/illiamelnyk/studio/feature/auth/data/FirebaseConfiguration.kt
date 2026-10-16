package com.illiamelnyk.studio.feature.auth.data

import com.illiamelnyk.studio.BuildConfig

internal data class FirebaseConfiguration(
    val appId: String,
    val apiKey: String,
    val projectId: String,
    val webClientId: String,
) {
    val isConfigured: Boolean get() = listOf(appId, apiKey, projectId, webClientId).all { it.isNotBlank() }

    companion object {
        fun fromBuild() = FirebaseConfiguration(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
    }
}
