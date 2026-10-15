package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.AppError
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecordingFilesTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun maximumSizedRecordingCopiesWithoutTruncation() {
        val source = File(context.cacheDir, "recording-max.wav")
        RandomAccessFile(source, "rw").use {
            it.setLength(20L * 1024 * 1024)
            it.seek(0)
            it.write("RIFF1234WAVE".toByteArray())
        }
        val files = RecordingFiles(context)
        val uri = files.copyToPrivateStorage(source.toUri().toString(), "recording-size-limit")
        val destination = File(java.net.URI(uri))
        try {
            assertEquals(20L * 1024 * 1024, destination.length())
            assertTrue(destination.name.endsWith(".wav"))
        } finally {
            destination.delete()
            source.delete()
        }
    }

    @Test
    fun oversizedRecordingFailsAndRemovesItsPartialPrivateCopy() {
        val source = File(context.cacheDir, "recording-oversize.wav")
        RandomAccessFile(source, "rw").use { it.setLength(20L * 1024 * 1024 + 1) }
        val id = "recording-oversize-private"
        try {
            RecordingFiles(context).copyToPrivateStorage(source.toUri().toString(), id)
            org.junit.Assert.fail("Oversized recording must be rejected")
        } catch (failure: ApiFailure) {
            assertEquals(AppError.InvalidReference, failure.error)
        } finally {
            source.delete()
        }
        assertFalse(context.filesDir.listFiles()!!.any { it.name.startsWith(id + ".") })
    }

    @Test
    fun unreadableSourceFailsWithoutLeavingAnEmptyPrivateDestination() {
        val source = File(context.cacheDir, "missing-recording-file.wav").apply { delete() }
        val id = "recording-unreadable-private"
        try {
            RecordingFiles(context).copyToPrivateStorage(source.toUri().toString(), id)
            org.junit.Assert.fail("Missing source must fail")
        } catch (_: Exception) {
            Unit
        }
        assertFalse(context.filesDir.listFiles()!!.any { it.name.startsWith(id + ".") })
    }
}
