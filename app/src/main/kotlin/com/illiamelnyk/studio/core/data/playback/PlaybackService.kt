package com.illiamelnyk.studio.core.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.illiamelnyk.studio.MainActivity
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackResumption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Media3 owns platform sessions, media buttons, notification and foreground-service lifecycle. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val playback: PlaybackController by inject()
    private val readiness: PlaybackReadiness by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trusted by lazy { TrustedMediaCaller(this) }
    private val artwork by lazy { AndroidMediaArtwork(this) }
    private lateinit var bridge: StudioSessionPlayer
    private var session: MediaLibrarySession? = null
    internal val libraryCallback: MediaLibrarySession.Callback by lazy { LibraryCallback() }
    private var artworkJob: Job? = null
    private var coverSeed: String? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(object : DefaultMediaNotificationProvider(this) {
            override fun getMediaButtons(
                session: MediaSession,
                playerCommands: Player.Commands,
                mediaButtonPreferences: ImmutableList<CommandButton>,
                showPauseButton: Boolean,
            ): ImmutableList<CommandButton> {
                if (playback.systemSnapshot()?.state?.isStation == true) {
                    return ImmutableList.of(
                        CommandButton.Builder(CommandButton.ICON_STOP)
                            .setPlayerCommand(Player.COMMAND_STOP).setDisplayName(getString(R.string.ui_stop))
                            .setExtras(
                                Bundle().apply {
                                    putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 0)
                                },
                            ).build(),
                    )
                }
                return super.getMediaButtons(session, playerCommands, mediaButtonPreferences, showPauseButton)
            }
        })
        bridge = StudioSessionPlayer(playback)
        session = MediaLibrarySession.Builder(this, bridge, libraryCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()
        // UI commands reach the domain controller directly, without binding a MediaController.
        // Register explicitly so Media3 observes playback and owns the foreground notification.
        addSession(requireNotNull(session))
        scope.launch {
            playback.state.collect {
                val snapshot = playback.systemSnapshot()
                bridge.refresh()
                val seed = snapshot?.state?.current?.coverSeed?.value
                if (seed != coverSeed) {
                    artworkJob?.cancel()
                    coverSeed = seed
                    bridge.cover = null
                    if (seed != null) {
                        artworkJob = scope.launch {
                            val loaded = artwork.load(seed)
                            if (playback.systemSnapshot()?.state?.current?.coverSeed?.value == seed) {
                                bridge.cover = loaded
                                bridge.refresh()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session.takeIf { trusted.accepts(controllerInfo.packageName, controllerInfo.uid) }

    override fun onDestroy() {
        scope.cancel()
        try {
            playback.releaseForServiceShutdown()
        } catch (failure: Exception) {
            android.util.Log.e("StudioPlayback", "Shutdown persistence failed: " + failure.javaClass.simpleName)
        } finally {
            session?.release()
            session = null
            bridge.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            super.onDestroy()
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> = Futures.immediateFuture(
            if (trusted.accepts(controller.packageName, controller.uid)) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            } else {
                MediaSession.ConnectionResult.reject()
            },
        )

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = afterReady(browser) { snapshot ->
            if (snapshot == null) {
                LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
            } else {
                LibraryResult.ofItem(recentRoot(snapshot), params)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = afterReady(browser) { snapshot ->
            val items = if (snapshot != null && parentId == recentRoot(snapshot).mediaId && page == 0) {
                listOf(recentMediaItem(snapshot, bridge.cover))
            } else {
                emptyList()
            }
            LibraryResult.ofItemList(items, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = afterReady(browser) { snapshot ->
            if (snapshot != null && mediaId == snapshot.mediaId) {
                LibraryResult.ofItem(recentMediaItem(snapshot, bridge.cover), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = afterReady(controller) { snapshot ->
            if (snapshot == null || mediaItems.size != 1 || mediaItems.single().mediaId != snapshot.mediaId) {
                throw SecurityException("Stale playback media ID")
            }
            MediaSession.MediaItemsWithStartPosition(
                listOf(recentMediaItem(snapshot, bridge.cover)),
                0,
                snapshot.state.positionMs,
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = afterReady(controller) { snapshot ->
            requireNotNull(snapshot) { "Playback owner unavailable" }
            MediaSession.MediaItemsWithStartPosition(
                listOf(recentMediaItem(snapshot, bridge.cover)),
                0,
                snapshot.state.positionMs,
            )
        }

        private fun <T> afterReady(
            caller: MediaSession.ControllerInfo,
            result: (PlaybackResumption?) -> T,
        ): ListenableFuture<T> {
            if (!trusted.accepts(caller.packageName, caller.uid)) {
                return Futures.immediateFailedFuture(SecurityException("Untrusted playback caller"))
            }
            val future = SettableFuture.create<T>()
            val job = scope.launch {
                try {
                    readiness.awaitReady()
                    // Revalidate caller and owner after startup, including library discovery.
                    if (!trusted.accepts(caller.packageName, caller.uid)) {
                        throw SecurityException("Untrusted playback caller")
                    }
                    future.set(result(playback.systemSnapshot()))
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (failure: Exception) {
                    future.setException(failure)
                }
            }
            future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
            return future
        }
    }
}
