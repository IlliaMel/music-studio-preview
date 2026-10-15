package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import java.io.File

internal class RecordingFiles(private val context: Context) {
    fun referenceDirectory(accountId: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
        val owner = digest.joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "references/" + owner).apply { mkdirs() }
    }

    fun deleteReferences(accountId: String, legacyUris: List<String>) {
        val directory = referenceDirectory(accountId)
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
        val legacyRoot = File(context.filesDir, "references").canonicalFile
        legacyUris.forEach { value ->
            val uri = value.toUri()
            if (uri.scheme == "file") {
                val source = uri.path?.let(::File)?.canonicalFile
                if (source?.parentFile == legacyRoot) source.delete()
            }
        }
    }

    fun copyToPrivateStorage(localUri: String, id: String): String {
        var destination = File(context.filesDir, id + ".audio")
        try {
            val uri = localUri.toUri()
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { input.copyTo(it) }
            } ?: throw ApiFailure(AppError.Unknown)
            val header = destination.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            val fallback = context.contentResolver.getType(uri) ?: audioTypeFromPath(localUri)
            val extension = audioExtension(audioContentType(header, fallback))
            if (extension != "audio") {
                val renamed = File(context.filesDir, id + "." + extension)
                if (!destination.renameTo(renamed)) throw ApiFailure(AppError.Unknown)
                destination = renamed
            }
            return destination.toUri().toString()
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun delete(track: Track) {
        track.audioUri?.let(::deleteLocal)
        deleteCached(track.id)
    }

    fun deleteCached(id: TrackId) {
        val hash = id.value.hashCode()
        File(context.cacheDir, "audio").listFiles()?.filter {
            it.name.startsWith("$hash.") || it.name.startsWith("demo-$hash.")
        }?.forEach { it.delete() }
    }

    private fun deleteLocal(uri: String) {
        if (uri.startsWith("file:")) {
            val file = File(uri.toUri().path ?: return)
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) file.delete()
        }
    }
}
