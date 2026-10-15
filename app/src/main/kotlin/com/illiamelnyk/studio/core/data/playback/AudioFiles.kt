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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AudioFiles(
    private val context: Context,
    private val ownerId: () -> String? = { null },
    private val authorization: suspend (
        Track,
    ) -> Map<String, String> = { throw SecurityException("Audio export is not authorized") },
    private val openConnection: (String) -> java.net.HttpURLConnection = { source ->
        (java.net.URI(source).toURL().openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects =
                false
        }
    },
) {
    suspend fun playableUri(track: Track): Uri = track.audioUri?.toUri() ?: Uri.fromFile(demo(track))

    suspend fun export(track: Track): File = withContext(Dispatchers.IO) {
        exportLock.withLock {
            val owner = ownerId() ?: throw SecurityException("Audio export requires a current account")
            val generation = playbackStateGeneration(owner)
            fun checkOwner() {
                if (ownerId() != owner || playbackStateGeneration(owner) != generation) {
                    throw SecurityException("Audio export account changed")
                }
            }
            val headers = authorization(track)
            checkOwner()
            val source = track.audioUri
            if (source == null) {
                val file = demo(track, owner, ::checkOwner)
                checkOwner()
                return@withLock file
            }
            val uri = source.toUri()
            val candidateExtension = uri.path?.substringAfterLast('.')?.lowercase()
            val extension = candidateExtension?.takeIf { it in AUDIO_EXTENSIONS } ?: "mp3"
            var target = File(context.cacheDir, "audio/${audioCacheKey(track.id, owner)}.$extension")
            target.parentFile?.mkdirs()
            val cacheFolder = target.parentFile
            val cacheStem = target.nameWithoutExtension
            val cached = AUDIO_EXTENSIONS.asSequence().map { File(cacheFolder, "$cacheStem.$it") }
                .firstOrNull { it.exists() && it.length() > 44 }
            if (cached != null) {
                checkOwner()
                return@withLock cached
            }
            val temporary = File(target.path + ".tmp")
            var completed = false
            var promoted = false
            suspend fun copy(input: java.io.InputStream) {
                input.use {
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            checkOwner()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            try {
                if (uri.scheme == "http" || uri.scheme == "https") {
                    val connection = openConnection(source)
                    connection.connectTimeout = 15000
                    connection.readTimeout = 60000
                    connection.instanceFollowRedirects = false
                    headers.forEach(connection::setRequestProperty)
                    try {
                        if (connection.contentType?.substringBefore(';') in
                            listOf("audio/wav", "audio/x-wav", "audio/wave")
                        ) {
                            target =
                                File(target.parentFile, target.nameWithoutExtension + ".wav")
                        }
                        check(connection.responseCode in 200..299) { "Audio HTTP ${connection.responseCode}" }
                        copy(connection.inputStream)
                    } finally {
                        connection.disconnect()
                    }
                } else {
                    copy(context.contentResolver.openInputStream(uri) ?: error("Audio is unavailable"))
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
                checkOwner()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(temporary.renameTo(target)) { "Cannot save audio" }
                promoted = true
                checkOwner()
                completed = true
                target
            } finally {
                if (promoted && !completed) target.delete()
                temporary.delete()
            }
        }
    }

    private suspend fun demo(
        track: Track,
        owner: String? = null,
        checkOwner: () -> Unit = {
        },
    ): File = withContext(Dispatchers.IO) {
        demoLock.withLock {
            val key = owner?.let { audioCacheKey(track.id, it) } ?: audioCacheKey(track.id)
            val file = File(context.cacheDir, "audio/demo-$key.wav")
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > 44) {
                checkOwner()
                return@withLock file
            }
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
            var promoted = false
            var completed = false
            try {
                checkOwner()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                temporary.writeBytes(data.array())
                checkOwner()
                check(temporary.renameTo(file))
                promoted = true
                checkOwner()
                completed = true
                file
            } finally {
                if (promoted && !completed) file.delete()
                temporary.delete()
            }
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = listOf("mp3", "wav", "m4a", "mp4", "ogg", "flac", "amr", "aac", "webm")
        private val demoLock = Mutex()
        private val exportLock = Mutex()
    }
}
