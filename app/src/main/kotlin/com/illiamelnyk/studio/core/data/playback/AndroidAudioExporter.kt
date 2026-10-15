package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.core.content.FileProvider
import com.illiamelnyk.studio.core.domain.playback.AudioExporter
import com.illiamelnyk.studio.core.domain.playback.ExportedAudio
import com.illiamelnyk.studio.core.domain.track.Track

class AndroidAudioExporter(
    private val context: Context,
    private val ownerId: () -> String?,
    private val authorization: suspend (Track) -> Map<String, String>,
) : AudioExporter {
    override suspend fun export(track: Track): ExportedAudio {
        val owner = ownerId() ?: throw SecurityException("Audio export requires a current account")
        val generation = playbackStateGeneration(owner)
        val file = AudioFiles(context, ownerId, authorization).export(track)
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
        if (ownerId() != owner || playbackStateGeneration(owner) != generation) {
            throw SecurityException("Audio export account changed")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.audio", file, name)
        return ExportedAudio(uri.toString(), name, mimeType)
    }
}
