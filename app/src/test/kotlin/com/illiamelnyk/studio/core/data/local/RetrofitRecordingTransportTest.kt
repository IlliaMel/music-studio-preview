package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.AppError
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RetrofitRecordingTransportTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val wave = "RIFF1234WAVErecording-data".toByteArray()
    private val id = "349346a0-577a-4c16-9caa-fbda97aaf16f"

    @Test fun cancellingAfterDownloadBodyStartsClosesTheCallAndNeverUploads() = runTest {
        localServer().use { server ->
            val bodyStarted = CountDownLatch(1)
            val completed = CountDownLatch(1)
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .eventListener(object : EventListener() {
                    override fun responseBodyStart(call: Call) {
                        bodyStarted.countDown()
                    }
                }).build()
            server.enqueue(MockResponse().setBody(Buffer().write(wave)).setBodyDelay(4, TimeUnit.SECONDS))
            val api = api(server, client)
            val job = launch(Dispatchers.IO) {
                api.upload(server.localUrl("/v1/recordings/$id/audio").toString(), "dev:fixture")
            }
            job.invokeOnCompletion { completed.countDown() }
            try {
                assertTrue("Download body must be reading before cancellation", bodyStarted.await(5, TimeUnit.SECONDS))
                job.cancel()
                assertTrue("Cancellation must close a stalled body read promptly", completed.await(2, TimeUnit.SECONDS))
                assertTrue(job.isCancelled)
                assertEquals(1, server.requestCount)
                assertEquals("/v1/recordings/$id/audio", server.takeRequest().path)
            } finally {
                job.cancelAndJoin()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun recordingPutSendsRawBytesAndUnicodeMetadataWithBearerOutsideTheUrl() = runTest {
        localServer().use { server ->
            val title = "Ідея №1"
            val file = File(context.cacheDir, "transport-recording.wav").apply { writeBytes(wave) }
            server.enqueue(MockResponse().setBody(recording(server.localUrl("/v1/recordings/$id/audio").toString())))
            api(server).putRecording(id, file.toUri().toString(), title, 12_000, "dev:fixture")
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/v1/recordings/$id", request.path)
            assertEquals("audio/wav", request.getHeader("Content-Type"))
            assertEquals("Bearer dev:fixture", request.getHeader("Authorization"))
            assertEquals(title, android.net.Uri.decode(request.getHeader("X-Recording-Title")))
            assertEquals("12000", request.getHeader("X-Recording-Duration-Ms"))
            assertTrue(wave.contentEquals(request.body.readByteArray()))
            assertFalse(request.path!!.contains("dev:fixture"))
        }
    }

    @Test
    fun restoredRecordingDownloadsOwnedAudioThenUploadsReference() = runTest {
        localServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(wave)))
            server.enqueue(MockResponse().setBody("""{"id":"temporary-reference"}"""))
            assertEquals(
                "temporary-reference",
                api(server).upload(server.localUrl("/v1/recordings/$id/audio").toString(), "dev:fixture"),
            )
            val download = server.takeRequest()
            val upload = server.takeRequest()
            assertEquals("/v1/recordings/$id/audio", download.path)
            assertEquals("/v1/uploads", upload.path)
            assertEquals("Bearer dev:fixture", download.getHeader("Authorization"))
            assertEquals("Bearer dev:fixture", upload.getHeader("Authorization"))
            assertTrue(wave.contentEquals(upload.body.readByteArray()))
        }
    }

    @Test
    fun foreignOriginsPathsAndQueriesAreRejectedBeforeAnyNetworkCall() = runTest {
        localServer().use { server ->
            val api = api(server)
            for (url in listOf(
                "https://evil.test/v1/recordings/$id/audio",
                server.localUrl("/other/audio").toString(),
                server.localUrl("/v1/recordings/$id/audio?redirect=evil").toString(),
            )) {
                try {
                    api.upload(url, "dev:fixture")
                    org.junit.Assert.fail("Unexpected URL accepted")
                } catch (failure: ApiFailure) {
                    assertEquals(AppError.InvalidReference, failure.error)
                }
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun redirectCannotForwardBearerToAnotherServer() = runTest {
        localServer().use { server ->
            localServer().use { foreign ->
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", foreign.localUrl("/stolen")))
                try {
                    api(server).upload(server.localUrl("/v1/recordings/$id/audio").toString(), "dev:fixture")
                    org.junit.Assert.fail("Redirect accepted")
                } catch (_: ApiFailure) {
                    assertEquals(1, server.requestCount)
                    assertEquals(0, foreign.requestCount)
                }
            }
        }
    }

    private fun MockWebServer.localUrl(path: String) = url(path).newBuilder().host("127.0.0.1").build()

    private fun localServer() = MockWebServer().apply {
        start(java.net.InetAddress.getByName("127.0.0.1"), 0)
    }

    private fun api(server: MockWebServer, calls: Call.Factory? = null) = StudioApi(
        context,
        ApiConfiguration(server.localUrl("/").toString().trimEnd('/'), ApiAuthMode.Development, true),
        callFactory = calls,
    )

    private fun recording(url: String) = """
        {"id":"$id","title":"idea","contentType":"audio/wav","sizeBytes":${wave.size},
        "durationMs":12000,"createdAt":"2026-10-21T00:00:00Z","updatedAt":"2026-10-21T00:00:00Z","audioUrl":"$url"}
    """.trimIndent()
}
