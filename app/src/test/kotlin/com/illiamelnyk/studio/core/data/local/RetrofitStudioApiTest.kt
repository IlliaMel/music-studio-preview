package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.network.ProfileUpdateDto
import com.illiamelnyk.studio.core.data.network.RetrofitStudioTransport
import com.illiamelnyk.studio.core.data.network.toCreateTrackDto
import com.illiamelnyk.studio.core.data.network.toTrack
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RetrofitStudioApiTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun backendValidationIssueArraysPreserveCodeAndClassification() = runTest {
        localServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(400).setBody(
                    """{"error":{"code":"VALIDATION","message":"The request is not valid","details":[{"path":"displayName","message":"Too long"}]}}""",
                ),
            )
            val result =
                failure { api(server).updateProfile("dev:fixture", ProfileUpdateDto(displayName = "invalid")) }
            assertEquals("VALIDATION", result.serverCode)
            assertEquals(AppError.InvalidPrompt(null), result.error)
        }
    }

    @Test fun emptySuccessBodiesAreAllowedOnlyForUnitEndpoints() = runTest {
        localServer().use { server ->
            val api = api(server)
            val calls: List<suspend () -> Unit> = listOf(
                { api.getProfile("dev:fixture") },
                { api.listTracks("dev:fixture") },
                { api.listLikes("dev:fixture") },
                { api.listPlaylists("dev:fixture") },
                { api.listRecordings("dev:fixture") },
                { api.getCatalog("dev:fixture") },
            )
            calls.forEach { call ->
                server.enqueue(MockResponse().setResponseCode(204))
                assertEquals(AppError.Server, failure { call() }.error)
            }
            server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(Unit, api.deleteAccount("dev:fixture"))
        }
    }

    @Test fun emptyTrackPageCannotEraseAnExistingDurableLibrary() = runTest {
        StudioStore.clearForTests(context)
        localServer().use { server ->
            val store = StudioStore(context)
            val track = DemoCatalog.tracks.first().copy(status = TrackStatus.Queued, audioUri = null)
            val account =
                AccountSnapshot(JSONObject().put("id", "owner").toSession(), "dev:fixture", tracks = listOf(track))
            store.change { it.put("active", "owner").put("account-owner", AccountJsonCodec.encode(account)) }
            val repository = DurableStudioRepository(context, store, api(server), backgroundScope)
            repository.synchronization.setForeground(false)
            server.enqueue(MockResponse().setBody("""{"id":"owner"}"""))
            server.enqueue(MockResponse().setBody("""{"trackIds":[]}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(Outcome.Failure(AppError.Server), repository.syncPending("owner"))
            assertEquals(listOf(track), repository.observeMine().first())
            val runtimeJobs = backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
            runtimeJobs.forEach { it.cancel() }
            runtimeJobs.forEach { it.join() }
            StudioStore.closeForTests(context)
            assertEquals(
                listOf(track),
                AccountJsonCodec.decode(StudioStore(context).read().getJSONObject("account-owner")).tracks,
            )
        }
    }

    @Test fun legacySingularRemixSurvivesTypedTransportButExplicitPluralWins() = runTest {
        localServer().use { server ->
            val api = api(server)
            val document = JSONObject().put("id", "track").put("status", "queued").put("prompt", "hello")
                .put("createdAt", "2026-10-21T00:00:00Z").put("remixTrackId", "legacy-source")
            server.enqueue(MockResponse().setBody(document.toString()))
            val legacy = api.getTrack("dev:fixture", com.illiamelnyk.studio.core.domain.track.TrackId("track"))
            assertEquals(null, legacy.remixTrackIds)
            assertEquals(listOf("legacy-source"), legacy.toTrack().remixTrackIds)
            server.enqueue(MockResponse().setBody(document.put("remixTrackIds", org.json.JSONArray()).toString()))
            val explicit = api.getTrack("dev:fixture", com.illiamelnyk.studio.core.domain.track.TrackId("track"))
            assertEquals(emptyList<String>(), explicit.remixTrackIds)
            assertEquals(emptyList<String>(), explicit.toTrack().remixTrackIds)
        }
    }

    @Test
    fun typedTrackPageAcceptsNullableServerFieldsAndKeepsCursorOpaque() = runTest {
        localServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"tracks":[{"id":"track","status":"queued","prompt":"hello","createdAt":"2026-10-21T00:00:00Z",
                "durationMs":null,"lengthMs":null,"title":null,"audioUrl":null,"error":null,"genres":[]}],"nextCursor":"next"}""",
                ),
            )
            val result = api(server).listTracks("dev:fixture", "a+b/=")
            assertEquals("queued", result.tracks.single().status)
            assertEquals("next", result.nextCursor)
            val request = server.takeRequest()
            assertEquals("a+b/=", request.requestUrl!!.queryParameter("cursor"))
            assertEquals("50", request.requestUrl!!.queryParameter("limit"))
            assertEquals("Bearer dev:fixture", request.getHeader("Authorization"))
        }
    }

    @Test
    fun errorEnvelopePreservesQuotaProviderUnknownAndRateLimitDistinctions() = runTest {
        localServer().use { server ->
            val api = api(server)
            for ((status, code, expected) in listOf(
                Triple(429, "QUOTA_EXCEEDED", AppError.ProviderUnavailable),
                Triple(429, "DAILY_LIMIT", AppError.QuotaExceeded),
                Triple(502, "PROVIDER_AUTH", AppError.ProviderUnavailable),
                Triple(429, "PROVIDER_UNAVAILABLE", AppError.ProviderUnavailable),
                Triple(429, "RATE_LIMITED", AppError.RateLimited),
                Triple(502, "PROVIDER_OUTCOME_UNKNOWN", AppError.ProviderOutcomeUnknown),
                Triple(401, "UNAUTHORIZED", AppError.Unauthorized),
            )) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":{"code":"$code"}}"""))
                val failure = failure { api.getProfile("dev:fixture") }
                assertEquals(expected, failure.error)
                assertEquals(code, failure.serverCode)
            }
            assertEquals(7, server.requestCount)
        }
    }

    @Test
    fun malformedSuccessIsServerFailureAndDoesNotOverwriteAValidSnapshot() = runTest {
        localServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":null}"""))
            assertEquals(AppError.Server, failure { api(server).getProfile("dev:fixture") }.error)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun requestCancellationCancelsTheCallWithoutRetrying() = runTest {
        localServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api = api(server)
            val job = launch { api.getProfile("dev:fixture") }
            runCurrent()
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun readsRetryOverAClosedPooledConnectionWhileWritesAreSentOnce() {
        localServer().use { server ->
            val calls = RetrofitStudioTransport(server.localUrl("/").toString()).calls
            fun send(method: String) = calls.newCall(
                Request.Builder()
                    .url(server.localUrl("/v1/capabilities"))
                    .method(method, if (method == "GET") null else "{}".toRequestBody())
                    .build(),
            ).execute().use { it.code }
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("{}"))
            assertEquals(200, send("GET"))
            assertEquals(200, send("GET"))
            assertEquals(3, server.requestCount)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertThrows(IOException::class.java) { send("POST") }
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun redirectedApiRequestNeverForwardsCredentials() = runTest {
        localServer().use { server ->
            localServer().use { foreign ->
                server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", foreign.localUrl("/stolen")))
                assertEquals(AppError.Server, failure { api(server).getProfile("dev:fixture") }.error)
                assertEquals(0, foreign.requestCount)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun createRequestDropsLocalOnlyFieldsAndKeepsStableAttemptKey() = runTest {
        localServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(
                    202,
                ).setBody(
                    """{"tracks":[{"id":"one","status":"queued","createdAt":"2026-10-21T00:00:00Z"},{"id":"two","status":"queued","createdAt":"2026-10-21T00:00:00Z"}]}""",
                ),
            )
            api(server).createTracks(
                "dev:fixture",
                GenerationRequest("warm piano melody", referenceAudioUri = "file:///private/source")
                    .toCreateTrackDto(variants = 2),
                "same-attempt-key",
            )
            val request = server.takeRequest()
            val body = JSONObject(request.body.readUtf8())
            assertEquals(2, body.getInt("variants"))
            assertFalse(body.has("referenceAudioUri"))
            assertFalse(body.has("lengthMs"))
            assertEquals("same-attempt-key", request.getHeader("Idempotency-Key"))
        }
    }

    @Test
    fun acceptedGenerationMustContainOneOrTwoTypedTracks() = runTest {
        localServer().use { server ->
            val api = api(server)
            for (response in listOf(
                "{\"tracks\":[]}",
                "{\"tracks\":[{\"id\":\"missing-required-status-and-time\"}]}",
            )) {
                server.enqueue(MockResponse().setResponseCode(202).setBody(response))
                assertEquals(
                    AppError.Server,
                    failure {
                        api.createTracks(
                            "dev:fixture",
                            com.illiamelnyk.studio.core.data.network.CreateTrackDto("Warm piano melody"),
                            "same-key",
                        )
                    }.error,
                )
            }
            assertEquals(2, server.requestCount)
        }
    }

    private fun MockWebServer.localUrl(path: String) = url(path).newBuilder().host("127.0.0.1").build()

    private fun localServer() = MockWebServer().apply {
        start(java.net.InetAddress.getByName("127.0.0.1"), 0)
    }

    private fun api(server: MockWebServer) = StudioApi(
        context,
        ApiConfiguration(server.localUrl("/").toString().trimEnd('/'), ApiAuthMode.Development, true),
    )

    private suspend fun failure(block: suspend () -> Unit): ApiFailure = try {
        block()
        throw AssertionError("Expected classified failure")
    } catch (failure: ApiFailure) {
        failure
    }
}
