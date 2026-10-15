package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.core.content.FileProvider
import com.illiamelnyk.studio.core.domain.playback.AudioExporter
import com.illiamelnyk.studio.core.domain.playback.ExportedAudio
import com.illiamelnyk.studio.core.domain.track.Track

class AndroidAudioExporter(private val context: Context) : AudioExporter {
    override suspend fun export(track: Track): ExportedAudio {
        val file = AudioFiles(context).export(track)
        val mimeType = when (file.extension) {
            "wav" -> "audio/wav"
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "amr" -> "audio/amr"
            "aac" -> "audio/aac"
            "webm" -> "audio/webm"
            else -> "audio/mpeg"
        }
        val title = track.title.filterNot(Char::isISOControl).replace('/', '_').replace('\\', '_')
            .ifBlank { "Studio track" }
        val name = title + "." + file.extension
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.audio", file, name)
        return ExportedAudio(uri.toString(), name, mimeType)
    }
}
