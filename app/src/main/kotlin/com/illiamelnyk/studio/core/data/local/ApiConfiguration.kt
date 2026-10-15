package com.illiamelnyk.studio.core.data.local

import java.net.URI

enum class ApiAuthMode { Disabled, Development, Firebase }

data class ApiConfiguration(val baseUrl: String, val authMode: ApiAuthMode, val debug: Boolean) {
    val isAvailable: Boolean
        get() {
            if (authMode == ApiAuthMode.Disabled || (!debug && authMode == ApiAuthMode.Development)) return false
            val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return false
            if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null ||
                uri.rawFragment != null
            ) {
                return false
            }
            if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return false
            if (uri.scheme == "https") return true
            return debug && authMode == ApiAuthMode.Development && uri.scheme == "http" &&
                uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost", "::1", "[::1]")
        }

    companion object {
        fun fromBuild(baseUrl: String, authMode: String, debug: Boolean): ApiConfiguration = ApiConfiguration(
            baseUrl.trimEnd('/'),
            when (authMode) {
                "development" -> ApiAuthMode.Development
                "firebase" -> ApiAuthMode.Firebase
                else -> ApiAuthMode.Disabled
            },
            debug,
        )
    }
}
