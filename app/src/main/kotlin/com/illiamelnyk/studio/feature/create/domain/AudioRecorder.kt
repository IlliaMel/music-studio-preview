package com.illiamelnyk.studio.feature.create.domain

data class RecordedAudio(val uri: String, val title: String, val durationMs: Long)

interface AudioRecorder {
    fun start()
    fun pause()
    fun resume()
    fun finish(): RecordedAudio
    fun amplitude(): Float
    fun elapsedMs(): Long
    fun release()
    suspend fun importAudio(uri: String): RecordedAudio
}
