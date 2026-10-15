package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Quality
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
        context.filesDir.listFiles()?.filter { it.name.startsWith("studio-state-v1.json") }?.forEach { it.delete() }
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
        val connection = FixtureConnection()
        val api = object : StudioApi(
            context,
            firebaseConfig,
            BearerTokenProvider {
                FirebaseBearerToken(it, "real.fixture.token", Long.MAX_VALUE)
            },
        ) {
            var opened = 0
            override fun openConnection(url: String): HttpURLConnection {
                opened++
                return connection
            }
        }
        connection.status = 503
        assertEquals(
            AppError.Server,
            failure {
                api.request("POST", "/v1/tracks", "firebase:uid", JSONObject().put("prompt", "Piano"), "stable-key")
            },
        )
        assertEquals(1, api.opened)
        assertEquals("Bearer real.fixture.token", connection.getRequestProperty("Authorization"))
        assertEquals("stable-key", connection.getRequestProperty("Idempotency-Key"))
        assertFalse(connection.sent.toString().contains("real.fixture.token"))
    }

    @Test
    fun realAuthorizationIsNotWrittenIntoTrackOrPlaybackJson() {
        val track = FakeCatalog.glassHarbor.copy(audioHeaders = mapOf("Authorization" to "Bearer private.id.token"))
        assertFalse(track.toJson().toString().contains("private.id.token"))
        val old = track.toJson().put("audioHeaders", JSONObject().put("authorization", "Bearer old.secret.token"))
        assertTrue(old.toTrack().audioHeaders.isEmpty())
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
                GenerationRequest("Gentle acoustic piano melody", remixTrackId = FakeCatalog.glassHarbor.id.value),
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
                GenerationRequest("Gentle acoustic piano melody", remixTrackId = FakeCatalog.glassHarbor.id.value),
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

    private class FixtureConnection : HttpURLConnection(URL("https://api.example.test")) {
        var status = 200
        val sent = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream("{}".toByteArray())
        override fun getErrorStream() = ByteArrayInputStream("{}".toByteArray())
        override fun getOutputStream() = sent
    }

    private class CapabilitiesApi(
        context: Context,
        config: ApiConfiguration = ApiConfiguration("http://10.0.2.2:8787", ApiAuthMode.Development, true),
    ) : StudioApi(context, config) {
        var document = liveCapabilities()
        var requests = 0
        var capabilityReads = 0
        var posts = 0
        var uploads = 0
        override suspend fun upload(uri: String, token: String): String {
            uploads++
            return "upload"
        }
        override suspend fun request(
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
