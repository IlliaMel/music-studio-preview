package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.illiamelnyk.studio.core.domain.AppError
import okhttp3.OkHttpClient

/** Decoder boundary. Queue, account ownership and durable resume remain the controller's responsibility. */
interface AudioEngine {
    val duration: Long
    val currentPosition: Long
    val isPlaying: Boolean

    /** The player means to play. It stays true while a short audio focus loss holds the sound back. */
    val playWhenReady: Boolean
    fun prepare(uri: Uri, headers: Map<String, String>, listener: Listener)

    /** Stop and detach the current authorized source without releasing the decoder. */
    fun clearSource()
    fun start()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()

    interface Listener {
        fun onReady()
        fun onEnded()
        fun onError(error: AppError)
        fun onInterrupted()
    }
}

/** Headers belong to this short-lived source, never the public MediaItem or persisted domain model. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class Media3AudioEngine(
    private val context: Context,
    private val playerFactory: () -> ExoPlayer = { ExoPlayer.Builder(context).build() },
) : AudioEngine {
    private var player: ExoPlayer? = null
    private var sourceListener: Player.Listener? = null
    private var released = false
    override val duration: Long get() = player?.duration?.takeUnless { it == C.TIME_UNSET } ?: 0
    override val currentPosition: Long get() = player?.currentPosition ?: 0
    override val isPlaying: Boolean get() = player?.isPlaying == true
    override val playWhenReady: Boolean get() = player?.playWhenReady == true

    override fun prepare(uri: Uri, headers: Map<String, String>, listener: AudioEngine.Listener) {
        check(!released) { "Released decoder cannot prepare another source" }
        clearSource()
        // Each source owns its request factory: credentials never persist on the reusable player.
        val http = authorizedAudioDataSource(headers)
        val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
            .createMediaSource(MediaItem.fromUri(uri))
        val next = player ?: playerFactory().also { player = it }
        next.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        next.setHandleAudioBecomingNoisy(true)
        // Server tracks stream over Wi-Fi: keep it awake too while the screen is off.
        next.setWakeMode(C.WAKE_MODE_NETWORK)
        val events = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> listener.onReady()

                    Player.STATE_ENDED -> listener.onEnded()

                    // Loading and initial idle are not completion or interruption events.
                    Player.STATE_BUFFERING, Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                listener.onError(media3Error(error))
            }

            // A lasting focus loss, a refused focus request or unplugged headphones pause for good.
            // A short loss (an assistant, a navigation prompt) only suppresses sound, and Media3
            // resumes by itself when focus returns, so it is not an interruption.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && (
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                        )
                ) {
                    listener.onInterrupted()
                }
            }
        }
        sourceListener = events
        next.addListener(events)
        next.setMediaSource(source)
        next.prepare()
    }

    override fun clearSource() {
        val active = player ?: return
        sourceListener?.let(active::removeListener)
        sourceListener = null
        active.stop()
        active.clearMediaItems()
        active.playWhenReady = false
    }

    override fun start() {
        player?.play()
    }
    override fun pause() {
        player?.pause()
    }
    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }
    override fun release() {
        if (released) return
        clearSource()
        released = true
        player?.release()
        player = null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun media3Error(error: PlaybackException): AppError {
    val response = error.cause as? HttpDataSource.InvalidResponseCodeException
    return when {
        response?.responseCode in listOf(401, 403) -> AppError.Unauthorized

        response?.responseCode == 429 -> AppError.RateLimited

        response != null && response.responseCode in 500..599 -> AppError.Server

        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> AppError.Timeout

        error.errorCode in listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        ) -> AppError.Network

        else -> AppError.Unknown
    }
}

// A redirect is an API error, never permission to send the bearer token to a new destination.
@androidx.annotation.OptIn(UnstableApi::class)
internal fun authorizedAudioDataSource(headers: Map<String, String>): OkHttpDataSource.Factory =
    OkHttpDataSource.Factory(audioHttpClient).setDefaultRequestProperties(headers)

private val audioHttpClient by lazy {
    OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
}
