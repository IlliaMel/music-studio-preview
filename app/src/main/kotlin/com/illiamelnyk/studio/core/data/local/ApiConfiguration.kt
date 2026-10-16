package com.illiamelnyk.studio.core.data.local

import java.net.URI

enum class ApiAuthMode { Disabled, Development, Firebase }

data class ApiConfiguration(
    val baseUrl: String,
    val authMode: ApiAuthMode,
    val debug: Boolean,
    val allowLanDevelopment: Boolean = false,
) {
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
                (
                    uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost", "::1", "[::1]") ||
                        (allowLanDevelopment && isPrivateIpv4(uri.host))
                    )
        }

    companion object {
        private fun isPrivateIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4 || parts.any { !Regex("0|[1-9][0-9]{0,2}").matches(it) }) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            return octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }

        fun fromBuild(
            baseUrl: String,
            authMode: String,
            debug: Boolean,
            allowLanDevelopment: Boolean = false,
        ): ApiConfiguration = ApiConfiguration(
            baseUrl.trimEnd('/'),
            when (authMode) {
                "development" -> ApiAuthMode.Development
                "firebase" -> ApiAuthMode.Firebase
                else -> ApiAuthMode.Disabled
            },
            debug,
            allowLanDevelopment,
        )
    }
}
