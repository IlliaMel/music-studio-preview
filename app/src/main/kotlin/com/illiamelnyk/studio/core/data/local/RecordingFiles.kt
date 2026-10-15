package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.data.playback.audioCacheKey
import com.illiamelnyk.studio.core.data.playback.clearPlaybackState
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import java.io.File
import java.io.IOException

internal class RecordingFiles(private val context: Context) {
    fun referenceDirectory(accountId: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
        val owner = digest.joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "references/" + owner).apply { mkdirs() }
    }

    fun deletePlaybackState(accountId: String) = clearPlaybackState(context, accountId)

    fun deleteReferences(accountId: String, legacyUris: List<String>) {
        val directory = referenceDirectory(accountId)
        directory.listFiles()?.forEach(::deleteChecked)
        deleteChecked(directory)
        val legacyRoot = File(context.filesDir, "references").canonicalFile
        legacyUris.forEach { value ->
            val uri = value.toUri()
            if (uri.scheme == "file") {
                val source = uri.path?.let(::File)?.canonicalFile
                if (source?.parentFile == legacyRoot) deleteChecked(source)
            }
        }
    }

    fun copyToPrivateStorage(localUri: String, id: String): String = copy(localUri, id, context.filesDir)

    fun copyToStaging(localUri: String, id: String, ownerId: String): String =
        copy(localUri, id, stagingDirectory(ownerId))

    fun deleteStagedExcept(ownerId: String, keepUris: List<String>) {
        val keep = keepUris.mapNotNull { value ->
            value.toUri().takeIf { it.scheme == "file" }?.path?.let(::File)?.canonicalPath
        }.toSet()
        stagingDirectory(ownerId).listFiles()?.filter { it.canonicalPath !in keep }?.forEach(::deleteChecked)
    }

    fun deleteRecordingStaging(ownerId: String) {
        val directory = stagingDirectory(ownerId)
        directory.listFiles()?.forEach(::deleteChecked)
        deleteChecked(directory)
    }

    private fun stagingDirectory(ownerId: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
        val owner = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(context.filesDir, "recording-staging/" + owner).apply { mkdirs() }
    }

    private fun copy(localUri: String, id: String, directory: File): String {
        var destination = File(directory, id + ".audio")
        try {
            val uri = localUri.toUri()
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 20L * 1024 * 1024) throw ApiFailure(AppError.InvalidReference)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw ApiFailure(AppError.Unknown)
            val header = destination.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            val fallback = context.contentResolver.getType(uri) ?: audioTypeFromPath(localUri)
            val extension = audioExtension(audioContentType(header, fallback))
            if (extension != "audio") {
                val renamed = File(directory, id + "." + extension)
                if (!destination.renameTo(renamed)) throw ApiFailure(AppError.Unknown)
                destination = renamed
            }
            return destination.toUri().toString()
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun deletePrivateCopy(uri: String) = deleteLocal(uri)

    fun delete(track: Track, ownerId: String? = null) = delete(track.id, track.audioUri, ownerId)

    fun delete(id: TrackId, audioUri: String?, ownerId: String? = null) {
        audioUri?.let(::deleteLocal)
        deleteCached(id, ownerId)
    }

    fun deleteCached(id: TrackId, ownerId: String? = null) {
        val legacy = audioCacheKey(id)
        val keys = listOfNotNull(legacy, ownerId?.let { audioCacheKey(id, it) })
        File(context.cacheDir, "audio").listFiles()?.filter { file ->
            keys.any { key -> file.name.startsWith("$key.") || file.name.startsWith("demo-$key.") }
        }?.forEach(::deleteChecked)
    }

    private fun deleteChecked(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Private audio cleanup failed")
    }

    private fun deleteLocal(uri: String) {
        if (uri.startsWith("file:")) {
            val file = File(uri.toUri().path ?: return)
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) deleteChecked(file)
        }
    }
}
