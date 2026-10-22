package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Quality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProductionApiTest {
    private lateinit var context: Context

    @Before
    fun cleanStore() {
        context = RuntimeEnvironment.getApplication()
        com.illiamelnyk.studio.core.data.local.StudioStore.clearForTests(context)
    }

    @Test
    fun releaseRequiresHttpsAndFirebaseWhileDebugHttpIsLoopbackOnly() {
        assertTrue(ApiConfiguration("https://api.example.test", ApiAuthMode.Firebase, false).isAvailable)
        assertFalse(ApiConfiguration("http://api.example.test", ApiAuthMode.Firebase, false).isAvailable)
        assertFalse(ApiConfiguration("https://api.example.test", ApiAuthMode.Development, false).isAvailable)
        assertTrue(ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Development, true).isAvailable)
        assertFalse(ApiConfiguration("http://api.example.test", ApiAuthMode.Development, true).isAvailable)
        assertFalse(ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Firebase, true).isAvailable)
        listOf(
            "https://u:p@api.example.test",
            "https://api.example.test/path",
            "https://api.example.test?q=1",
            "https://api.example.test#fragment",
        )
            .forEach { assertFalse(ApiConfiguration(it, ApiAuthMode.Firebase, false).isAvailable) }
        assertFalse(ApiConfiguration.fromBuild("https://api.example.test", "misspelled", false).isAvailable)
    }

    @Test
    fun lanHttpRequiresExplicitDebugOptInAndPrivateIpv4() {
        listOf("192.168.1.243", "10.1.2.3", "172.16.0.1", "172.31.255.254").forEach { host ->
            val url = "http://$host:8789"
            assertFalse(ApiConfiguration(url, ApiAuthMode.Development, true).isAvailable)
            assertTrue(ApiConfiguration.fromBuild(url, "development", true, true).isAvailable)
            assertFalse(ApiConfiguration(url, ApiAuthMode.Development, false, true).isAvailable)
            assertFalse(ApiConfiguration(url, ApiAuthMode.Firebase, true, true).isAvailable)
        }
        listOf(
            "172.15.0.1", "172.32.0.1", "192.169.1.2", "8.8.8.8", "0.0.0.0", "pc.local",
            "10.0.0.999", "010.1.2.3", "172.016.0.1",
        )
            .forEach { host ->
                assertFalse(ApiConfiguration("http://$host:8789", ApiAuthMode.Development, true, true).isAvailable)
            }
    }

    @Test
    fun firebaseBearerIsRefreshedPerCallAndNeverFallsBackToDevOrAnotherOwner() = runTest {
        var calls = 0
        var mismatch = false
        var expired = false
        val api = StudioApi(
            context,
            firebaseConfig,
            BearerTokenProvider { uid ->
                calls++
                FirebaseBearerToken(
                    if (mismatch) "other" else uid,
                    "header.payload.signature" + calls,
                    if (expired) 1_000 else 100_000,
                )
            },
            nowMillis = { 1_000 },
        )
        assertEquals("header.payload.signature1", api.authorization("firebase:google-uid"))
        assertEquals("header.payload.signature2", api.authorization("firebase:google-uid"))
        assertEquals("Bearer header.payload.signature2", api.audioHeaders("firebase:google-uid")["Authorization"])
        assertTrue(api.audioHeaders("firebase:other").isEmpty())
        mismatch = true
        assertEquals(AppError.Unauthorized, failure { api.authorization("firebase:google-uid") })
        assertTrue(api.audioHeaders("firebase:google-uid").isEmpty())
        mismatch = false
        expired = true
        assertEquals(AppError.Unauthorized, failure { api.authorization("firebase:google-uid") })
        assertEquals(AppError.Unauthorized, failure { api.authorization("dev:illia") })
        assertEquals(4, calls)
        val missing = StudioApi(context, firebaseConfig)
        assertEquals(AppError.Unauthorized, failure { missing.authorization("firebase:google-uid") })
    }

    @Test
    fun realIdBearerAndIdempotencyReachOnePostWithoutAutomaticRetry() = runTest {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.enqueue(
                okhttp3.mockwebserver.MockResponse().setResponseCode(503)
                    .setBody("{\"error\":{\"code\":\"INTERNAL\"}}"),
            )
            val client = okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).build()
            val api = StudioApi(
                context,
                firebaseConfig,
                BearerTokenProvider { FirebaseBearerToken(it, "real.fixture.token", Long.MAX_VALUE) },
                callFactory = okhttp3.Call.Factory { request ->
                    client.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build())
                },
            )
            assertEquals(
                AppError.Server,
                failure {
                    api.createTracks(
                        "firebase:uid",
                        com.illiamelnyk.studio.core.data.network.CreateTrackDto("Piano"),
                        "stable-key",
                    )
                },
            )
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("Bearer real.fixture.token", request.getHeader("Authorization"))
            assertEquals("stable-key", request.getHeader("Idempotency-Key"))
            assertFalse(request.body.readUtf8().contains("real.fixture.token"))
            assertFalse(request.path!!.contains("real.fixture.token"))
        }
    }

    @Test
    fun realAuthorizationIsNotWrittenIntoTrackOrPlaybackJson() {
        val track = DemoCatalog.glassHarbor
        assertFalse(track.toJson().toString().contains("private.id.token"))
        val old = track.toJson().put("audioHeaders", JSONObject().put("authorization", "Bearer old.secret.token"))
        assertFalse(old.toTrack().toJson().has("audioHeaders"))
        assertFalse(old.toTrack().toJson().toString().contains("old.secret.token"))
    }

    @Test
    fun unsupportedQualityAndReferencesCannotUploadOrPostOrSpendQuota() = runTest {
        val api = CapabilitiesApi(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertTrue(repository.signInServer() is Outcome.Success)
        val before = repository.credits.first()
        assertEquals(
            Outcome.Failure(AppError.UnsupportedMode),
            repository.create(
                GenerationRequest(
                    "Gentle acoustic piano melody",
                    quality = Quality.Deep,
                    referenceAudioUri = "content://private/recording",
                ),
                1,
            ),
        )
        assertEquals(
            Outcome.Failure(AppError.UnsupportedMode),
            repository.create(
                GenerationRequest("Gentle acoustic piano melody", referenceAudioUri = "content://private/recording"),
                1,
            ),
        )
        assertEquals(
            Outcome.Failure(AppError.UnsupportedMode),
            repository.create(
                GenerationRequest(
                    "Gentle acoustic piano melody",
                    remixTrackIds = listOf(DemoCatalog.glassHarbor.id.value),
                ),
                1,
            ),
        )
        assertEquals(0, api.uploads)
        assertEquals(0, api.posts)
        assertEquals(3, api.capabilityReads)
        assertEquals(before, repository.credits.first())
        assertTrue(repository.observeMine().first().isEmpty())
    }

    @Test
    fun malformedCapabilitiesFailClosedBeforeAnyPaidMutation() = runTest {
        val api = CapabilitiesApi(context)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        repository.signInServer()
        api.document = JSONObject().put("provider", "elevenlabs")
        assertEquals(
            Outcome.Failure(AppError.Server),
            repository.create(GenerationRequest("Gentle acoustic piano melody"), 1),
        )
        assertEquals(0, api.posts)
        assertEquals(0, api.uploads)
    }

    @Test
    fun liveLyricsConstraintsAndCapabilitiesAreTypedWithoutSilentDowngrade() {
        val capabilities = GenerationCapabilities.parse(liveCapabilities())
        assertEquals(
            AppError.UnsupportedMode,
            capabilities.rejection(
                GenerationRequest("Gentle acoustic piano melody", customLyrics = "a".repeat(201)),
                1,
            ),
        )
        assertEquals(
            null,
            capabilities.rejection(
                GenerationRequest("Gentle acoustic piano melody", customLyrics = "Short lyric line"),
                2,
            ),
        )
        assertEquals(
            AppError.UnsupportedMode,
            capabilities.rejection(GenerationRequest("Gentle acoustic piano melody"), 3),
        )
        val mock = GenerationCapabilities.parse(
            liveCapabilities().put("provider", "mock").put("audioReferences", "mock"),
        )
        assertEquals(
            null,
            mock.rejection(
                GenerationRequest(
                    "Gentle acoustic piano melody",
                    remixTrackIds = listOf(DemoCatalog.glassHarbor.id.value),
                ),
                1,
            ),
        )
    }

    @Test
    fun releaseGoogleEntryCannotCreateDevelopmentAccount() = runTest {
        val api = CapabilitiesApi(context, firebaseConfig)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertEquals(Outcome.Failure(AppError.UnsupportedMode), repository.signInServer())
        assertEquals(0, api.requests)
        assertEquals(null, repository.session.first())
    }

    @Test
    fun sdkBridgeKeepsFirebaseUidSeparateFromServerOwnerAndPersistsOnlyReference() = runTest {
        val api = CapabilitiesApi(context, firebaseConfig)
        val repository = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        assertTrue(repository.signInFirebase("actual-google-uid") is Outcome.Success)
        assertEquals("server-owner", repository.session.first()?.user?.id?.value)
        assertEquals(SessionDataMode.FirebaseServer, repository.session.first()?.dataMode)
        assertEquals(
            "firebase:actual-google-uid",
            StudioStore(context).read().getJSONObject("account-server-owner").getString("token"),
        )
        assertEquals(null, repository.session.first()?.user?.displayName?.takeIf { it == "Illia Melnyk" })
    }

    private suspend fun failure(action: suspend () -> Any): AppError? = try {
        action()
        null
    } catch (failure: ApiFailure) {
        failure.error
    }

    private class CapabilitiesApi(
        context: Context,
        config: ApiConfiguration = ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Development, true),
    ) : JsonFixtureStudioApi(context, config) {
        var document = liveCapabilities()
        var requests = 0
        var capabilityReads = 0
        var posts = 0
        var uploads = 0
        override suspend fun upload(uri: String, token: String): String {
            uploads++
            return "upload"
        }
        override suspend fun requestFixture(
            method: String,
            path: String,
            token: String,
            body: JSONObject?,
            key: String?,
        ): JSONObject {
            requests++
            if (path == "/v1/capabilities") {
                capabilityReads++
                return document
            }
            if (path == "/v1/me") return JSONObject().put("id", "server-owner").put("displayName", "Google name")
            if (path == "/v1/me/quota") return JSONObject().put("limit", 25).put("remaining", 25)
            if (method == "POST") posts++
            throw ApiFailure(AppError.Server)
        }
    }

    companion object {
        private val firebaseConfig = ApiConfiguration("https://api.example.test", ApiAuthMode.Firebase, false)
        private fun liveCapabilities() = JSONObject().put("provider", "elevenlabs")
            .put("qualityModes", JSONArray(listOf("auto"))).put("customLyrics", true)
            .put("variants", JSONArray(listOf(1, 2))).put("audioReferences", "disabled-enterprise-entitlement-required")
            .put("liveCatalogRemix", false)
    }
}
