package com.illiamelnyk.studio.feature.create.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.net.toUri
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.feature.create.domain.AudioRecorder
import com.illiamelnyk.studio.feature.create.domain.RecordedAudio
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAudioRecorder(
    private val context: Context,
    private val referenceDirectory: () -> File = { File(context.filesDir, "references") },
) : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var elapsed = 0L
    private var paused = false

    @Suppress("DEPRECATION")
    override fun start() {
        release()
        val directory = referenceDirectory().apply { mkdirs() }
        file = File(directory, "idea-" + UUID.randomUUID() + ".m4a")
        val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = instance
        try {
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioEncodingBitRate(128_000)
            instance.setAudioSamplingRate(44_100)
            instance.setOutputFile(requireNotNull(file).absolutePath)
            instance.prepare()
            instance.start()
            startedAt = SystemClock.elapsedRealtime()
            elapsed = 0L
            paused = false
        } catch (error: Exception) {
            release()
            file?.delete()
            throw error
        }
    }

    override fun pause() {
        recorder?.pause()
        elapsed = elapsedMs()
        paused = true
    }

    override fun resume() {
        recorder?.resume()
        startedAt = SystemClock.elapsedRealtime()
        paused = false
    }

    override fun elapsedMs(): Long =
        elapsed + if (recorder != null && !paused) SystemClock.elapsedRealtime() - startedAt else 0L

    override fun amplitude(): Float = if (paused) 0f else (recorder?.maxAmplitude ?: 0) / 32767f

    override fun finish(): RecordedAudio {
        val duration = elapsedMs()
        val output = requireNotNull(file)
        try {
            requireNotNull(recorder).stop()
            require(duration >= 10_000) { "Record at least 10 seconds" }
            require(output.parentFile?.canonicalFile == referenceDirectory().canonicalFile) { "Account changed" }
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            release()
        }
        return RecordedAudio(
            Uri.fromFile(output).toString(),
            context.getString(R.string.record_idea, java.time.LocalTime.now().withNano(0).toString()),
            duration,
        )
    }

    override fun release() {
        recorder?.release()
        recorder = null
    }

    override suspend fun importAudio(uri: String): RecordedAudio = withContext(Dispatchers.IO) {
        val source = uri.toUri()
        val directory = referenceDirectory().apply { mkdirs() }
        val output = File(directory, "audio-" + UUID.randomUUID() + ".audio")
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input)
                output.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 20 * 1024 * 1024) { "Audio exceeds 20 MB" }
                        target.write(buffer, 0, count)
                    }
                }
            }
            val metadata = MediaMetadataRetriever()
            val duration = try {
                metadata.setDataSource(output.absolutePath)
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            } finally {
                metadata.release()
            }
            require(duration in 1_000..300_000) { "Audio must be 1 second to 5 minutes" }
            require(output.parentFile?.canonicalFile == referenceDirectory().canonicalFile) { "Account changed" }
            RecordedAudio(Uri.fromFile(output).toString(), "Audio reference", duration)
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }
}
