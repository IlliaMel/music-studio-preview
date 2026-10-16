package com.illiamelnyk.studio.core.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState as PlatformState
import android.os.Build
import android.os.Bundle
import android.service.media.MediaBrowserService
import com.illiamelnyk.studio.MainActivity
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackResumption
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PlaybackService : MediaBrowserService() {
    private val playback: PlaybackController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private val trusted by lazy { TrustedMediaCaller(this) }
    private val artwork by lazy { AndroidMediaArtwork(this) }
    private var artworkJob: Job? = null
    private var cover: Bitmap? = null
    private var coverSeed: String? = null
    private var foreground = false
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (playback.systemSnapshot()?.state?.isPlaying == true) playback.togglePlayPause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        session = MediaSession(this, "StudioPlayback").apply {
            setSessionActivity(openActivity())
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume(null)
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = resume(mediaId)
                override fun onPause() = command { if (it.isPlaying && !it.isStation) playback.togglePlayPause() }
                override fun onSkipToNext() = command { if (!it.isStation) playback.next() }
                override fun onSkipToPrevious() = command { if (!it.isStation) playback.previous() }
                override fun onSeekTo(pos: Long) = command { if (!it.isStation) playback.seekTo(pos) }
                override fun onStop() = command { playback.stop() }
            })
        }
        sessionToken = session.sessionToken
        scope.launch {
            var previousControls: PlaybackState? = null
            var previousSecond = -1L
            playback.state.collect {
                val state = playback.systemSnapshot()?.state ?: PlaybackState()
                val controls = state.copy(positionMs = 0)
                val second = state.positionMs / 1000
                if (controls == previousControls && second == previousSecond) return@collect
                previousSecond = second
                session.isActive = state.current != null
                if (controls != previousControls) {
                    previousControls = controls
                    val seed = state.current?.coverSeed?.value
                    if (seed != coverSeed) {
                        artworkJob?.cancel()
                        coverSeed = seed
                        cover = null
                        if (seed != null) {
                            artworkJob = scope.launch {
                                val loaded = artwork.load(seed)
                                val current = playback.systemSnapshot()?.state
                                if (current?.current?.coverSeed?.value == seed) {
                                    cover = loaded
                                    updateMetadata(current)
                                    refreshNotification()
                                }
                            }
                        }
                    }
                    updateMetadata(state)
                    refreshNotification()
                }
                session.setPlaybackState(
                    PlatformState.Builder().setActions(actions(state)).setState(
                        when {
                            state.current == null -> PlatformState.STATE_NONE
                            state.isPlaying -> PlatformState.STATE_PLAYING
                            else -> PlatformState.STATE_PAUSED
                        },
                        state.positionMs,
                        if (state.isPlaying) 1f else 0f,
                    ).build(),
                )
                if (state.current == null && foreground) {
                    foreground = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (!trusted.accepts(clientPackageName, clientUid)) return null
        val snapshot = playback.systemSnapshot() ?: return null
        return BrowserRoot(ROOT + snapshot.mediaId, Bundle().apply { putBoolean(BrowserRoot.EXTRA_RECENT, true) })
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowser.MediaItem>>) {
        val snapshot = playback.systemSnapshot()
        if (snapshot == null || parentId != ROOT + snapshot.mediaId || !trustedBrowser()) {
            result.sendResult(emptyList())
            return
        }
        result.detach()
        scope.launch {
            val bitmap = artwork.load(requireNotNull(snapshot.state.current).coverSeed.value)
            val current = playback.systemSnapshot()
            result.sendResult(
                if (current != null && current.mediaId == snapshot.mediaId &&
                    current.state.current?.coverSeed == snapshot.state.current?.coverSeed
                ) {
                    listOf(recentMediaItem(current, bitmap))
                } else {
                    emptyList()
                },
            )
        }
    }

    private fun trustedBrowser(): Boolean {
        if (Build.VERSION.SDK_INT < 28) return true
        val caller = currentBrowserInfo
        return trusted.accepts(caller.packageName, caller.uid)
    }

    private fun trustedController(): Boolean {
        if (Build.VERSION.SDK_INT < 28) return true
        val caller = session.currentControllerInfo
        return trusted.accepts(caller.packageName, caller.uid)
    }

    private fun command(action: (PlaybackState) -> Unit) {
        if (!trustedController()) return
        playback.systemSnapshot()?.state?.let(action)
    }

    private fun resume(mediaId: String?) {
        if (!trustedController()) return
        resumeSystemPlayback(playback, mediaId)
    }

    private fun updateMetadata(state: PlaybackState) {
        session.setMetadata(mediaMetadata(state, cover))
    }

    private fun refreshNotification() {
        if (foreground) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun actions(state: PlaybackState): Long {
        if (state.current == null) return 0
        if (state.isStation) return PlatformState.ACTION_STOP or PlatformState.ACTION_PLAY
        var result = PlatformState.ACTION_PLAY_PAUSE or PlatformState.ACTION_PLAY or PlatformState.ACTION_PAUSE or
            PlatformState.ACTION_PLAY_FROM_MEDIA_ID or PlatformState.ACTION_SKIP_TO_PREVIOUS or
            PlatformState.ACTION_SEEK_TO or PlatformState.ACTION_STOP
        if (state.hasNext) result = result or PlatformState.ACTION_SKIP_TO_NEXT
        return result
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground = true
        startForeground(NOTIFICATION, notification())
        val snapshot = playback.systemSnapshot()
        if (snapshot == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getStringExtra(MEDIA_ID) == snapshot.mediaId) {
            when (intent.action) {
                "pause" -> if (!snapshot.state.isStation) playback.togglePlayPause()
                "next" -> if (!snapshot.state.isStation) playback.next()
                "previous" -> if (!snapshot.state.isStation) playback.previous()
                "stop" -> playback.stop()
            }
        }
        return START_NOT_STICKY
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name)
            .putExtra(MEDIA_ID, playback.systemSnapshot()?.mediaId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun transport(icon: Int, label: Int, name: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), action(name)).build()

    private fun notification(): Notification {
        val state = playback.systemSnapshot()?.state ?: PlaybackState()
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.current?.title ?: getString(R.string.app_name))
            .setContentText(state.current?.artist.orEmpty())
            .setContentIntent(openActivity()).setLargeIcon(cover)
            .setOngoing(state.isPlaying).setOnlyAlertOnce(true).setDeleteIntent(action("stop"))
        val count = if (state.isStation) {
            builder.addAction(transport(android.R.drawable.ic_menu_close_clear_cancel, R.string.ui_stop, "stop"))
            1
        } else {
            builder.addAction(transport(android.R.drawable.ic_media_previous, R.string.ui_previous, "previous"))
            val icon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val label = if (state.isPlaying) R.string.ui_pause else R.string.ui_play
            builder.addAction(transport(icon, label, "pause"))
            if (state.hasNext) {
                builder.addAction(transport(android.R.drawable.ic_media_next, R.string.ui_next, "next"))
                3
            } else {
                2
            }
        }
        return builder.setStyle(
            Notification.MediaStyle().setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(*IntArray(count) { it }),
        ).build()
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        scope.cancel()
        try {
            playback.releaseForServiceShutdown()
        } catch (failure: Exception) {
            android.util.Log.e("StudioPlayback", "Shutdown persistence failed: " + failure.javaClass.simpleName)
        } finally {
            session.isActive = false
            session.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            super.onDestroy()
        }
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION = 1001
        private const val ROOT = "recent:"
        private const val MEDIA_ID = "mediaId"
    }
}

internal fun mediaMetadata(state: PlaybackState, cover: Bitmap?): MediaMetadata = MediaMetadata.Builder()
    .putString(MediaMetadata.METADATA_KEY_TITLE, state.current?.title.orEmpty())
    .putString(MediaMetadata.METADATA_KEY_ARTIST, state.current?.artist.orEmpty())
    .putLong(MediaMetadata.METADATA_KEY_DURATION, if (state.isStation) -1 else state.durationMs)
    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
    .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, cover)
    .build()

internal fun recentMediaItem(snapshot: PlaybackResumption, cover: Bitmap?): MediaBrowser.MediaItem {
    val track = requireNotNull(snapshot.state.current)
    val description = MediaDescription.Builder().setMediaId(snapshot.mediaId)
        .setTitle(track.title).setSubtitle(track.artist).setIconBitmap(cover).build()
    return MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)
}

internal fun resumeSystemPlayback(playback: PlaybackController, mediaId: String?): Boolean {
    val snapshot = playback.systemSnapshot() ?: return false
    if (mediaId != null && mediaId != snapshot.mediaId) return false
    if (!snapshot.state.isPlaying) playback.togglePlayPause()
    return true
}
