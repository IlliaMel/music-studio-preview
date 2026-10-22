package com.illiamelnyk.studio.feature.create.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class AndroidAudioRecorderTest {
    private lateinit var context: Context
    private lateinit var directory: File

    @Before
    fun prepare() {
        context = RuntimeEnvironment.getApplication()
        directory = File(context.cacheDir, "encoded-recording-duration").apply { mkdirs() }
        directory.listFiles()?.forEach { assertTrue(it.delete()) }
    }

    @Test
    fun importedAudioRejectsShortDurationAndUnsupportedFormatBeforeAttachment() = runTest {
        val source = File(context.cacheDir, "import-source.wav")
        source.writeBytes("RIFF1234WAVEaudio-fixture".toByteArray())
        val short = AndroidAudioRecorder(context, { directory }) { 2_999L }
        try {
            short.importAudio(source.toURI().toString())
            fail("A reference shorter than three seconds must not be attached")
        } catch (_: IllegalArgumentException) { }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        source.writeBytes("OggSunsupported-fixture".toByteArray())
        val ogg = AndroidAudioRecorder(context, { directory }) { 30_000L }
        try {
            ogg.importAudio(source.toURI().toString())
            fail("OGG must not be attached to the MP3/M4A/WAV upload pipeline")
        } catch (_: IllegalArgumentException) { }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun threeSecondWaveImportKeepsAnAccountOwnedCopy() = runTest {
        val source = File(context.cacheDir, "valid-import.wav").apply {
            writeBytes("RIFF1234WAVEaudio-fixture".toByteArray())
        }
        val recorder = AndroidAudioRecorder(context, { directory }) { 3_000L }
        val imported = recorder.importAudio(source.toURI().toString())
        assertEquals(3_000L, imported.durationMs)
        val copy = File(java.net.URI(imported.uri))
        assertEquals(directory.canonicalFile, copy.parentFile.canonicalFile)
        assertTrue(copy.readBytes().contentEquals(source.readBytes()))
    }

    @Test
    fun longWallClockWithTruncatedEncodedAudioIsRejectedAndPrivateFileRemoved() {
        var encoded: File? = null
        val recorder = AndroidAudioRecorder(context, { directory }) { output ->
            encoded = output
            output.writeBytes("truncated encoded fixture".toByteArray())
            260L
        }
        recorder.start()
        ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(26_422))
        assertTrue(recorder.elapsedMs() >= 26_422)

        try {
            recorder.finish()
            fail("A 260 ms encoded recording must not be accepted as 26 seconds")
        } catch (_: IllegalArgumentException) {
            Unit
        }

        assertFalse(requireNotNull(encoded).exists())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun savedRecordingUsesEncodedDurationInsteadOfLongerWallClock() {
        val recorder = AndroidAudioRecorder(context, { directory }) { output ->
            output.writeBytes("complete encoded fixture".toByteArray())
            12_000L
        }
        recorder.start()
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(26))
        assertTrue(recorder.elapsedMs() >= 26_000)

        val saved = recorder.finish()

        assertEquals(12_000L, saved.durationMs)
        assertTrue(File(java.net.URI(saved.uri)).exists())
        assertEquals(directory.canonicalFile, File(java.net.URI(saved.uri)).parentFile.canonicalFile)
    }

    @Test
    fun missingEncodedDurationIsRejectedRatherThanFallingBackToWallClock() {
        var encoded: File? = null
        val recorder = AndroidAudioRecorder(context, { directory }) { output ->
            encoded = output
            output.writeBytes("missing metadata fixture".toByteArray())
            0L
        }
        recorder.start()
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(30))

        try {
            recorder.finish()
            fail("Missing encoded duration must not fall back to the timer")
        } catch (_: IllegalArgumentException) {
            Unit
        }

        assertFalse(requireNotNull(encoded).exists())
    }
}
