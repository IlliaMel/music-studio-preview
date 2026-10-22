package com.illiamelnyk.studio.core.data.playback

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.illiamelnyk.studio.core.domain.AppError
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Media3AudioSourceTest {
    @Test
    fun privateAudioSendsResolvedHeadersButNeverFollowsRedirects() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var received: String? = null
        var redirected = false
        server.createContext("/private") { request ->
            received = request.requestHeaders.getFirst("Authorization")
            request.responseHeaders.add("Location", "/untrusted")
            request.sendResponseHeaders(302, -1)
            request.close()
        }
        server.createContext("/untrusted") { request ->
            redirected = true
            request.sendResponseHeaders(200, -1)
            request.close()
        }
        server.start()
        val source = authorizedAudioDataSource(mapOf("Authorization" to "Bearer fresh-fixture")).createDataSource()
        try {
            val result = runCatching {
                source.open(DataSpec(Uri.parse("http://127.0.0.1:${server.address.port}/private")))
            }
            assertTrue(result.exceptionOrNull() is HttpDataSource.InvalidResponseCodeException)
            assertEquals("Bearer fresh-fixture", received)
            assertFalse(redirected)
        } finally {
            source.close()
            server.stop(0)
        }
    }

    @Test
    fun media3TransportErrorsBecomeTypedPlaybackFailures() {
        assertEquals(
            AppError.Timeout,
            media3Error(
                PlaybackException(
                    "timeout",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                ),
            ),
        )
        assertEquals(
            AppError.Network,
            media3Error(
                PlaybackException(
                    "offline",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                ),
            ),
        )
        assertEquals(
            AppError.Unknown,
            media3Error(
                PlaybackException(
                    "decoder",
                    null,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                ),
            ),
        )
    }
}
