package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.local.RecordingFiles
import com.illiamelnyk.studio.core.data.local.StudioApi
import com.illiamelnyk.studio.core.data.local.StudioStore
import com.illiamelnyk.studio.core.data.local.read
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.feature.player.fake.track
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExportOwnerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val song = track("recording-8ced07a1-2964-4a3f-aa80-75177020463b", 12.seconds)
        .copy(
            audioUri = "https://api.studio.test/v1/recordings/8ced07a1-2964-4a3f-aa80-75177020463b/audio",
        )

    @Before
    fun clean() {
        File(context.cacheDir, "audio").listFiles()?.forEach { it.delete() }
        com.illiamelnyk.studio.core.data.local.StudioStore.clearForTests(context)
    }

    @Test
    fun identicalRecordingUuidUsesDifferentOwnerCachesAndDeletionPreservesOtherOwnerBytes() = runTest {
        var owner = "owner-a"
        var calls = 0
        val files = AudioFiles(context, { owner }, { emptyMap() }) {
            calls++
            FixtureConnection(wave(if (owner == "owner-a") 1 else 2))
        }
        val first = files.export(song)
        owner = "owner-b"
        val second = files.export(song)
        assertEquals(2, calls)
        assertFalse(first == second)
        assertEquals(1, first.readBytes().last().toInt())
        assertEquals(2, second.readBytes().last().toInt())
        RecordingFiles(context).deleteCached(song.id, "owner-a")
        assertFalse(first.exists())
        assertTrue(second.exists())
    }

    @Test
    fun cacheHitsStillAuthorizeAndNetworkExportsNeverUseExpiredTrackHeaderSnapshots() = runTest {
        var tokens = 0
        var authorized = true
        val connections = mutableListOf<FixtureConnection>()
        val files = AudioFiles(context, { "owner" }, {
            if (!authorized) throw SecurityException("Expired session")
            tokens++
            mapOf("Authorization" to "Bearer fresh-" + tokens)
        }) { FixtureConnection(wave(1)).also(connections::add) }
        val file = files.export(song)
        assertEquals("Bearer fresh-1", connections.single().getRequestProperty("Authorization"))
        assertEquals(file, files.export(song))
        assertEquals(2, tokens)
        assertEquals(1, connections.size)
        authorized = false
        assertTrue(runCatching { files.export(song) }.isFailure)
        authorized = true
        file.delete()
        files.export(song)
        assertEquals("Bearer fresh-3", connections.last().getRequestProperty("Authorization"))
        assertFalse(connections.last().instanceFollowRedirects)
    }

    @Test
    fun ownerSwitchDuringDownloadCannotPublishOrReturnTheStaleOwnerFile() = runTest {
        var owner: String? = "owner-a"
        val files = AudioFiles(context, { owner }, { emptyMap() }) {
            FixtureConnection(wave(1)) { owner = "owner-b" }
        }
        assertTrue(runCatching { files.export(song) }.isFailure)
        assertTrue(File(context.cacheDir, "audio").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun accountPurgeDuringDownloadRejectsTheResultEvenIfOwnerIdRemainsTheSame() = runTest {
        val files = AudioFiles(context, { "purged-owner" }, { emptyMap() }) {
            FixtureConnection(wave(1)) { clearPlaybackState(context, "purged-owner") }
        }
        assertTrue(runCatching { files.export(song) }.isFailure)
        assertTrue(File(context.cacheDir, "audio").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun exportResolverRejectsUnownedLocalSourcesAndSignedOutCachedTracks() = runTest {
        val repository = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        repository.signInDemo()
        val source = File(context.cacheDir, "export-private-source.wav").apply { writeBytes(wave(1)) }
        val saved = (
            repository.saveRecording(
                source.toUri().toString(),
                "Owned recording",
                12.seconds,
            ) as Outcome.Success
            ).value
        assertTrue(repository.exportHeaders(saved).isEmpty())
        assertTrue(runCatching { repository.exportHeaders(saved.copy(audioUri = source.toUri().toString())) }.isFailure)
        repository.signOut()
        assertTrue(runCatching { repository.exportHeaders(saved) }.isFailure)
    }

    @Test
    fun exporterWithoutAnOwnerCannotReturnPreviouslyCachedAudio() = runTest {
        AudioFiles(context, { "owner" }, { emptyMap() }) { FixtureConnection(wave(1)) }.export(song)
        assertTrue(runCatching { AudioFiles(context).export(song) }.isFailure)
    }

    private fun wave(value: Int): ByteArray = ByteArray(128) { value.toByte() }.also {
        "RIFF".toByteArray().copyInto(it)
        "WAVE".toByteArray().copyInto(it, 8)
    }

    private class FixtureConnection(private val bytes: ByteArray, private val duringRead: (() -> Unit)? = null) :
        HttpURLConnection(URL("https://api.studio.test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "audio/wav"
        override fun getInputStream(): InputStream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val result = super.read(buffer, offset, length)
                duringRead?.invoke()
                return result
            }
        }
    }
}
