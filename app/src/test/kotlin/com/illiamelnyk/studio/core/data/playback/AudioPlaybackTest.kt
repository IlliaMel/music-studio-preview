package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.feature.player.fake.track
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioPlaybackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @org.junit.After
    fun clearPlaybackFixture() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `local demo exports audible WAV with declared track duration`() = runTest {
        val song = track("audio-fixture", 5.seconds)
        val file = AudioFiles(context).export(song)
        val bytes = file.readBytes()
        assertEquals("wav", file.extension)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16000, buffer.getInt(24))
        assertEquals(16000 * 5 * 2, buffer.getInt(40))
        assertTrue(bytes.drop(44).any { it.toInt() != 0 })
        assertEquals(file, AudioFiles(context).export(song))
    }

    @Test
    fun `export identifies imported MPEG4 audio instead of falsely labelling MP3`() = runTest {
        val source = java.io.File(context.filesDir, "reference-container.audio")
        val bytes = ByteArray(64)
        "ftyp".toByteArray().copyInto(bytes, destinationOffset = 4)
        source.writeBytes(bytes)
        val song = track("container-fixture", 5.seconds).copy(audioUri = android.net.Uri.fromFile(source).toString())
        val exported = AudioFiles(context).export(song)
        assertEquals("m4a", exported.extension)
        org.junit.Assert.assertArrayEquals(bytes, exported.readBytes())
    }

    @Test
    fun `custom lyrics survive and are timed across the track`() = runTest {
        val song = track("custom-audio", 20.seconds).copy(customLyrics = "First line\nSecond line")
        val tracks = com.illiamelnyk.studio.feature.player.fake.InMemoryTrackRepository(mine = listOf(song))
        val outcome = LocalLyricsRepository(tracks).lyrics(song.id)
        val lines = (outcome as com.illiamelnyk.studio.core.domain.Outcome.Success).value
        assertEquals(listOf("First line", "Second line"), lines.map { it.text })
        assertEquals(10000L, lines[1].startMs)
        assertEquals(20000L, lines.last().endMs)
    }

    @Test
    fun `saved playback restores queue position and pauses instead of autoplaying`() = runTest {
        val song = track("restore-audio", 20.seconds)
        val saved = JSONObject().put("queue", JSONArray().put(song.toJson()))
            .put("index", 0).put("position", 4250).put("shuffle", true).put("repeat", "All")
        context.getSharedPreferences("playback", Context.MODE_PRIVATE)
            .edit().putString("state", saved.toString()).commit()
        val playback = AndroidPlaybackController(context, backgroundScope)
        val restored = playback.state.value
        assertNotNull(restored.current)
        assertEquals(song.id, restored.current?.id)
        assertEquals(4250L, restored.positionMs)
        assertTrue(restored.shuffle)
        assertFalse(restored.isPlaying)
        assertEquals(com.illiamelnyk.studio.core.domain.playback.RepeatMode.All, restored.repeat)
    }
}
