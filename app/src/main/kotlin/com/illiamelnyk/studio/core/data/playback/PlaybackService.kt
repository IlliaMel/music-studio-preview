package com.illiamelnyk.studio.core.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as PlatformState
import android.os.IBinder
import com.illiamelnyk.studio.MainActivity
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PlaybackService : Service() {
    private val playback: PlaybackController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (playback.state.value.isPlaying) playback.togglePlayPause()
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
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!playback.state.value.isPlaying) playback.togglePlayPause()
                }
                override fun onPause() {
                    val state = playback.state.value
                    if (state.isPlaying && !state.isStation) playback.togglePlayPause()
                }
                override fun onSkipToNext() {
                    if (!playback.state.value.isStation) playback.next()
                }
                override fun onSkipToPrevious() {
                    if (!playback.state.value.isStation) playback.previous()
                }
                override fun onSeekTo(pos: Long) {
                    if (!playback.state.value.isStation) playback.seekTo(pos)
                }
                override fun onStop() = playback.stop()
            })
            isActive = true
        }
        startForeground(NOTIFICATION, notification())
        scope.launch {
            var previousControls: PlaybackState? = null
            var previousSecond = -1L
            playback.state.collect { state ->
                val controls = state.copy(positionMs = 0)
                val controlsChanged = controls != previousControls
                val second = state.positionMs / 1000
                if (!controlsChanged && second == previousSecond) return@collect
                previousSecond = second
                if (controlsChanged) {
                    previousControls = controls
                    session.setMetadata(
                        MediaMetadata.Builder()
                            .putString(MediaMetadata.METADATA_KEY_TITLE, state.current?.title.orEmpty())
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.current?.artist.orEmpty())
                            .putLong(MediaMetadata.METADATA_KEY_DURATION, if (state.isStation) -1 else state.durationMs)
                            .build(),
                    )
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
                }
                session.setPlaybackState(
                    PlatformState.Builder()
                        .setActions(actions(state))
                        .setState(
                            if (state.isPlaying) PlatformState.STATE_PLAYING else PlatformState.STATE_PAUSED,
                            state.positionMs,
                            if (state.isPlaying) 1f else 0f,
                        ).build(),
                )
                if (state.current == null) stopSelf()
            }
        }
    }

    private fun actions(state: PlaybackState): Long {
        if (state.current == null) return 0
        if (state.isStation) return PlatformState.ACTION_STOP or PlatformState.ACTION_PLAY
        var result = PlatformState.ACTION_PLAY_PAUSE or PlatformState.ACTION_PLAY or PlatformState.ACTION_PAUSE or
            PlatformState.ACTION_SKIP_TO_PREVIOUS or PlatformState.ACTION_SEEK_TO or PlatformState.ACTION_STOP
        if (state.hasNext) result = result or PlatformState.ACTION_SKIP_TO_NEXT
        return result
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> if (!playback.state.value.isStation) playback.togglePlayPause()
            "next" -> if (!playback.state.value.isStation) playback.next()
            "previous" -> if (!playback.state.value.isStation) playback.previous()
            "stop" -> playback.stop()
        }
        return START_NOT_STICKY
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun transport(icon: Int, label: Int, name: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), action(name)).build()

    private fun notification(): Notification {
        val state = playback.state.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.current?.title ?: getString(R.string.app_name))
            .setContentText(state.current?.artist.orEmpty())
            .setContentIntent(open)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(action("stop"))
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
        session.isActive = false
        session.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION = 1001
    }
}
