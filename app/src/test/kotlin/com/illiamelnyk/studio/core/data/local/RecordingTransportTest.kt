package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.AppError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecordingTransportTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val wave = "RIFF1234WAVErecording-data".toByteArray()
    private val id = "349346a0-577a-4c16-9caa-fbda97aaf16f"

    @Test
    fun recordingPutSendsRawBytesAndUnicodeMetadataWithBearerOutsideTheUrl() = runTest {
        val file = File(context.cacheDir, "transport-recording.wav").apply { writeBytes(wave) }
        val connection = FixtureConnection("{}".toByteArray())
        val api = FixtureApi(listOf(connection))
        api.putRecording(id, file.toUri().toString(), "Ідея №1", 12_000, "dev:illia")
        assertEquals("PUT", connection.requestMethod)
        assertEquals("audio/wav", connection.getRequestProperty("Content-Type"))
        assertEquals("Bearer dev:illia", connection.getRequestProperty("Authorization"))
        assertEquals("Ідея №1", android.net.Uri.decode(connection.getRequestProperty("X-Recording-Title")))
        assertEquals("12000", connection.getRequestProperty("X-Recording-Duration-Ms"))
        assertTrue(wave.contentEquals(connection.sent.toByteArray()))
        assertEquals(listOf("https://api.studio.test/v1/recordings/" + id), api.urls)
        assertFalse(api.urls.single().contains("dev:illia"))
    }

    @Test
    fun restoredRecordingReferenceDownloadsOwnedAudioThenUsesTheTemporaryUploadContract() = runTest {
        val download = FixtureConnection(wave)
        val upload = FixtureConnection("{\"id\":\"temporary-reference\"}".toByteArray())
        val api = FixtureApi(listOf(download, upload))
        assertEquals(
            "temporary-reference",
            api.upload("https://api.studio.test/v1/recordings/" + id + "/audio", "dev:illia"),
        )
        assertEquals(2, api.urls.size)
        assertEquals("https://api.studio.test/v1/uploads", api.urls.last())
        assertEquals("Bearer dev:illia", download.getRequestProperty("Authorization"))
        assertEquals("Bearer dev:illia", upload.getRequestProperty("Authorization"))
        assertTrue(wave.contentEquals(upload.sent.toByteArray()))
        assertFalse(download.instanceFollowRedirects)
    }

    @Test
    fun remoteReferenceCannotSendBearerToForeignOriginsOrUnexpectedPaths() = runTest {
        val api = FixtureApi(emptyList())
        for (url in listOf(
            "https://evil.test/v1/recordings/" + id + "/audio",
            "https://api.studio.test/other/audio",
            "https://api.studio.test/v1/recordings/" + id + "/audio?redirect=evil",
        )) {
            try {
                api.upload(url, "dev:illia")
                org.junit.Assert.fail("Unexpected audio URL must be rejected")
            } catch (failure: ApiFailure) {
                assertEquals(AppError.InvalidReference, failure.error)
            }
        }
        assertTrue(api.urls.isEmpty())
    }

    private class FixtureApi(private val connections: List<FixtureConnection>) :
        StudioApi(
            RuntimeEnvironment.getApplication(),
            ApiConfiguration("https://api.studio.test", ApiAuthMode.Development, true),
        ) {
        val urls = mutableListOf<String>()
        override fun openConnection(url: String): HttpURLConnection {
            urls += url
            return connections[urls.lastIndex].apply { instanceFollowRedirects = false }
        }
    }

    private class FixtureConnection(private val response: ByteArray) :
        HttpURLConnection(URL("https://api.studio.test")) {
        val sent = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getInputStream() = ByteArrayInputStream(response)
        override fun getOutputStream() = sent
    }
}
