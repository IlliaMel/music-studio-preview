package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.net.Uri
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.playback.PlaybackMetadataSnapshot
import com.illiamelnyk.studio.core.domain.track.Track
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Explicit decoder fake: tests can send late callbacks even after release to exercise ownership fences. */
internal class FakeAudioEngine : AudioEngine {
    override var duration: Long = 20_000
    override var currentPosition: Long = 0
    override var isPlaying = false
    override var playWhenReady = false
    var startCount = 0
    var released = false
    var uri: Uri? = null
    var headers: Map<String, String> = emptyMap()
    private var listener: AudioEngine.Listener? = null
    val listeners = mutableListOf<AudioEngine.Listener>()
    var prepareCount = 0
    var clearCount = 0
    override fun prepare(uri: Uri, headers: Map<String, String>, listener: AudioEngine.Listener) {
        check(!released)
        prepareCount++
        listeners += listener
        this.uri = uri
        this.headers = headers
        this.listener = listener
    }
    override fun clearSource() {
        clearCount++
        isPlaying = false
        playWhenReady = false
        currentPosition = 0
        uri = null
        headers = emptyMap()
        listener = null
    }
    override fun start() {
        if (released) return
        startCount++
        isPlaying = true
        playWhenReady = true
    }
    override fun pause() {
        isPlaying = false
        playWhenReady = false
    }
    override fun seekTo(positionMs: Long) {
        currentPosition = positionMs
    }
    override fun release() {
        released = true
        isPlaying = false
        playWhenReady = false
    }
    fun ready() {
        listener?.onReady()
    }
    fun ended() {
        isPlaying = false
        listener?.onEnded()
    }
    fun error(error: AppError = AppError.Unknown) {
        listener?.onError(error)
    }
    fun interrupted() {
        isPlaying = false
        playWhenReady = false
        listener?.onInterrupted()
    }

    /** A short audio focus loss: no sound, but the player still means to play and resumes itself. */
    fun suppressed(active: Boolean) {
        isPlaying = !active
    }
}

internal fun testPlaybackController(
    context: Context,
    scope: CoroutineScope,
    ownerId: () -> String?,
    random: Random = Random.Default,
    audioHeaders: suspend (Track) -> Map<String, String> = { emptyMap() },
    metadata: Flow<PlaybackMetadataSnapshot?> = emptyFlow(),
    isMetadataCurrent: (PlaybackMetadataSnapshot) -> Boolean = { true },
    engineFactory: () -> AudioEngine = { FakeAudioEngine() },
): AndroidPlaybackController = AndroidPlaybackController(
    context,
    scope,
    ownerId,
    random,
    audioHeaders,
    metadata,
    isMetadataCurrent,
    engineFactory,
)
