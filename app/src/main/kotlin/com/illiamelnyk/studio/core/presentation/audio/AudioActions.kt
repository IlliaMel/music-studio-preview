package com.illiamelnyk.studio.core.presentation.audio

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.illiamelnyk.studio.core.domain.playback.AudioExporter
import com.illiamelnyk.studio.core.domain.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioActions(private val exporter: AudioExporter) {
    suspend fun share(context: Context, track: Track) {
        val audio = exporter.export(track)
        val uri = audio.contentUri.toUri()
        val intent = Intent(Intent.ACTION_SEND).setType(audio.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, track.title)
            .apply { clipData = ClipData.newRawUri(track.title, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(intent, track.title)) }
    }

    suspend fun download(context: Context, track: Track) = withContext(Dispatchers.IO) {
        val audio = exporter.export(track)
        val source = audio.contentUri.toUri()
        val resolver = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            val folder = checkNotNull(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC))
            val target = java.io.File(folder, audio.fileName)
            checkNotNull(resolver.openInputStream(source)).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            return@withContext FileProvider.getUriForFile(context, "${context.packageName}.audio", target)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, audio.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, audio.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MusicStudio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { output ->
                checkNotNull(resolver.openInputStream(source)).use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        uri
    }
}
