package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.network.CapabilitiesDto
import com.illiamelnyk.studio.core.data.network.ProfileDto
import com.illiamelnyk.studio.core.data.network.toSession
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.track.GenerationOptions
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.Quality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StudioGenerationOptionsRepositoryTest {
    private lateinit var context: Context

    @Before
    fun clean() {
        context = RuntimeEnvironment.getApplication()
        StudioStore.clearForTests(context)
    }

    @Test
    fun connectedProviderExposesOnlyItsActualModesAndEntitlements() = runBlocking {
        val api = CapabilitiesApi(context)
        val repository = repository(api, "dev:fixture")

        assertEquals(
            Outcome.Success(GenerationOptions(setOf(Quality.Auto), setOf(1), false, false, false, 0)),
            repository.load(),
        )
        assertEquals(listOf("dev:fixture"), api.tokens)
    }

    @Test
    fun enabledEntitlementsAndKnownQualityModesMapWithoutPermissiveDefaults() = runBlocking {
        val api = CapabilitiesApi(context).apply {
            capabilities = capabilities.copy(
                qualityModes = listOf("fast", "auto"),
                variants = listOf(2),
                customLyrics = true,
                audioReferences = "enabled-enterprise",
                liveCatalogRemix = true,
                maxRemixSources = 2,
            )
        }
        assertEquals(
            Outcome.Success(GenerationOptions(setOf(Quality.Auto, Quality.Fast), setOf(2), true, true, true, 2)),
            repository(api, "dev:fixture").load(),
        )
    }

    @Test
    fun malformedCapabilitiesReturnServerFailureInsteadOfAllowingGeneration() = runBlocking {
        val api = CapabilitiesApi(context)
        val repository = repository(api, "dev:fixture")
        val valid = api.capabilities
        listOf(
            valid.copy(qualityModes = emptyList()),
            valid.copy(qualityModes = listOf("turbo")),
            valid.copy(variants = emptyList()),
            valid.copy(variants = listOf(3)),
            valid.copy(audioReferences = "unknown"),
            valid.copy(maxRemixSources = -1),
            valid.copy(provider = "unknown"),
        ).forEach { malformed ->
            api.capabilities = malformed
            assertEquals(Outcome.Failure(AppError.Server), repository.load())
        }
    }

    @Test
    fun cancellationPropagatesThroughCapabilityValidationAndAccountOperation() = runBlocking {
        val cancelled = CancellationException("Caller left the composer")
        val api = CapabilitiesApi(context).apply { failure = cancelled }
        val repository = repository(api, "dev:fixture")
        try {
            repository.load()
            fail("Cancellation must propagate instead of becoming an Outcome failure")
        } catch (actual: CancellationException) {
            assertEquals(cancelled.message, actual.message)
        }
    }

    @Test
    fun localDemoUsesItsOwnOptionsWithoutCallingTheConnectedProvider() = runBlocking {
        val api = CapabilitiesApi(context).apply { failure = AssertionError("Local demo must stay local") }
        assertEquals(
            Outcome.Success(GenerationOptions(Quality.entries.toSet(), setOf(1, 2), true, true, true, 4)),
            repository(api, null).load(),
        )
        assertEquals(emptyList<String>(), api.tokens)
    }

    private suspend fun repository(api: StudioApi, token: String?): StudioGenerationOptionsRepository {
        val store = StudioStore(context)
        store.saveAccount(
            AccountSnapshot(
                session = ProfileDto("owner").toSession(
                    if (token == null) SessionDataMode.LocalDemo else SessionDataMode.DevelopmentServer,
                ),
                token = token,
            ),
        )
        return StudioGenerationOptionsRepository(
            StudioAccountRuntime(
                context,
                store,
                api,
                Dispatchers.IO,
                GenerationWorkScheduler.None,
                GenerationNotifications.None,
            ),
        )
    }

    private class CapabilitiesApi(context: Context) :
        StudioApi(
            context,
            ApiConfiguration("http://127.0.0.1:8080", ApiAuthMode.Development, true),
        ) {
        val tokens = mutableListOf<String>()
        var failure: Throwable? = null
        var capabilities = CapabilitiesDto(
            provider = "elevenlabs",
            qualityModes = listOf("auto"),
            customLyrics = false,
            variants = listOf(1),
            audioReferences = "disabled-enterprise-entitlement-required",
            liveCatalogRemix = false,
            maxRemixSources = 0,
        )

        override suspend fun getCapabilities(token: String): CapabilitiesDto {
            tokens += token
            failure?.let { throw it }
            return capabilities
        }
    }
}
