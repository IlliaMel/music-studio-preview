package com.illiamelnyk.studio.feature.create.data

import android.content.Context
import java.io.File
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
