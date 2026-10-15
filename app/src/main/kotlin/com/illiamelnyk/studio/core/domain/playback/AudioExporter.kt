package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.track.Track

data class ExportedAudio(val contentUri: String, val fileName: String, val mimeType: String)

interface AudioExporter {
    suspend fun export(track: Track): ExportedAudio
}
