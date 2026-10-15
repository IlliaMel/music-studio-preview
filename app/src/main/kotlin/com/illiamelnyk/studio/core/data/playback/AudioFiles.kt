package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.track.Track
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AudioFiles(private val context: Context) {
    suspend fun playableUri(track: Track): Uri = track.audioUri?.toUri() ?: Uri.fromFile(demo(track))

    suspend fun export(track: Track): File = withContext(Dispatchers.IO) {
        exportLock.withLock {
            val source = track.audioUri
            if (source == null) return@withLock demo(track)
            val uri = source.toUri()
            val candidateExtension = uri.path?.substringAfterLast('.')?.lowercase()
            val extension = candidateExtension?.takeIf { it in AUDIO_EXTENSIONS } ?: "mp3"
            var target = File(context.cacheDir, "audio/${audioCacheKey(track.id)}.$extension")
            target.parentFile?.mkdirs()
            val cacheFolder = target.parentFile
            val cacheStem = target.nameWithoutExtension
            val cached = AUDIO_EXTENSIONS.asSequence().map { File(cacheFolder, "$cacheStem.$it") }
                .firstOrNull { it.exists() && it.length() > 44 }
            if (cached != null) return@withLock cached
            val temporary = File(target.path + ".tmp")
            try {
                if (uri.scheme == "http" || uri.scheme == "https") {
                    val connection = java.net.URI(source).toURL().openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 60000
                    track.audioHeaders.forEach(connection::setRequestProperty)
                    try {
                        if (connection.contentType?.substringBefore(';') in
                            listOf("audio/wav", "audio/x-wav", "audio/wave")
                        ) {
                            target =
                                File(target.parentFile, target.nameWithoutExtension + ".wav")
                        }
                        check(connection.responseCode in 200..299) { "Audio HTTP ${connection.responseCode}" }
                        connection.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                    } finally {
                        connection.disconnect()
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporary.outputStream().use { input.copyTo(it) }
                    } ?: error("Audio is unavailable")
                }
                check(temporary.length() > 44) { "Empty audio" }
                val header = ByteArray(16)
                temporary.inputStream().use { it.read(header) }
                val detected = when {
                    String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE" -> "wav"
                    String(header, 4, 4) == "ftyp" -> "m4a"
                    String(header, 0, 4) == "OggS" -> "ogg"
                    String(header, 0, 4) == "fLaC" -> "flac"
                    String(header, 0, 5) == "#!AMR" -> "amr"
                    (header[0].toInt() and 255) == 255 && (header[1].toInt() and 246) == 240 -> "aac"
                    header.take(4).map { it.toInt() and 255 } == listOf(26, 69, 223, 163) -> "webm"
                    else -> target.extension
                }
                target = File(target.parentFile, target.nameWithoutExtension + "." + detected)
                check(temporary.renameTo(target)) { "Cannot save audio" }
                target
            } finally {
                temporary.delete()
            }
        }
    }

    private suspend fun demo(track: Track): File = withContext(Dispatchers.IO) {
        demoLock.withLock {
            val file = File(context.cacheDir, "audio/demo-${audioCacheKey(track.id)}.wav")
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > 44) return@withLock file
            val rate = 16000
            val samples = track.duration.inWholeSeconds.coerceIn(5, 300).toInt() * rate
            val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
            val notes = doubleArrayOf(220.0, 261.63, 329.63, 293.66, 196.0, 246.94, 293.66, 261.63)
            repeat(samples) { index ->
                val time = index.toDouble() / rate
                val frequency = notes[(time * 2).toInt() % notes.size]
                val envelope = sin(PI * ((time * 2) % 1)).coerceAtLeast(0.0)
                val sound = (sin(2 * PI * frequency * time) + 0.3 * sin(2 * PI * frequency * 2 * time)) * envelope
                data.putShort((sound * 6000).toInt().toShort())
            }
            val temporary = File(file.path + ".tmp")
            temporary.writeBytes(data.array())
            check(temporary.renameTo(file))
            file
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = listOf("mp3", "wav", "m4a", "mp4", "ogg", "flac", "amr", "aac", "webm")
        private val demoLock = Mutex()
        private val exportLock = Mutex()
    }
}
