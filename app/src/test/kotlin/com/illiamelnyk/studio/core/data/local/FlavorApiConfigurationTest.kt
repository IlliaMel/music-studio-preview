package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorApiConfigurationTest {
    @Test
    fun productionDebugCannotEnableDevelopmentSignInEvenWithLocalUrlAndLanFlag() {
        for (url in listOf("http://localhost:8787", "http://192.168.1.42:8787", "https://api.example.test")) {
            val config = ApiConfiguration.fromBuild(url, "development", true, true, "prod")
            assertEquals(ApiAuthMode.Disabled, config.authMode)
            assertFalse(config.isAvailable)
            assertFalse(config.allowLanDevelopment)
        }
    }

    @Test
    fun bothReleaseEnvironmentsRejectDevelopmentAuthWhileConfiguredFirebaseUsesHttpsOnly() {
        for (environment in listOf("dev", "prod")) {
            assertFalse(
                ApiConfiguration.fromBuild(
                    "http://localhost:8787",
                    "development",
                    false,
                    true,
                    environment,
                ).isAvailable,
            )
            assertFalse(
                ApiConfiguration.fromBuild("http://api.example.test", "firebase", false, true, environment).isAvailable,
            )
            assertTrue(
                ApiConfiguration.fromBuild(
                    "https://api.example.test",
                    "firebase",
                    false,
                    false,
                    environment,
                ).isAvailable,
            )
            assertFalse(ApiConfiguration.fromBuild("", "firebase", false, false, environment).isAvailable)
        }
    }

    @Test
    fun localDevelopmentAndExplicitPrivateLanRemainLimitedToDevDebug() {
        assertTrue(ApiConfiguration.fromBuild("http://10.0.2.2:8787", "development", true, false, "dev").isAvailable)
        assertFalse(
            ApiConfiguration.fromBuild("http://192.168.1.42:8787", "development", true, false, "dev").isAvailable,
        )
        assertTrue(ApiConfiguration.fromBuild("http://192.168.1.42:8787", "development", true, true, "dev").isAvailable)
        assertFalse(ApiConfiguration.fromBuild("http://api.example.test", "development", true, true, "dev").isAvailable)
    }

    @Test
    fun generatedProductionFlavorNeverContainsDevelopmentAuthOrCleartextOptIn() {
        if (BuildConfig.FLAVOR == "prod") {
            assertFalse(BuildConfig.API_AUTH_MODE == "development")
            assertFalse(BuildConfig.API_ALLOW_LAN_DEVELOPMENT)
            val configuration = ApiConfiguration.fromBuild(
                BuildConfig.API_BASE_URL,
                BuildConfig.API_AUTH_MODE,
                BuildConfig.DEBUG,
                BuildConfig.API_ALLOW_LAN_DEVELOPMENT,
                BuildConfig.FLAVOR,
            )
            if (configuration.isAvailable) {
                assertEquals(ApiAuthMode.Firebase, configuration.authMode)
                assertTrue(configuration.baseUrl.startsWith("https://"))
            }
        }
    }
}
