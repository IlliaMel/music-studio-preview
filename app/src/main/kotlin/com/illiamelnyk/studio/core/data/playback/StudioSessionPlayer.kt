package com.illiamelnyk.studio.core.data.playback

import android.graphics.Bitmap
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Track

/**
 * Publishes sanitized, owner-scoped metadata and forwards system commands through the same domain
 * controller as the UI. A session controller never gets a private audio URL or source headers.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class StudioSessionPlayer(private val playback: PlaybackController) :
    SimpleBasePlayer(Looper.getMainLooper()) {
    private var artworkData: ByteArray? = null
    private var artworkEpoch = 0L
    private data class QueueProjectionKey(
        val mediaId: String,
        val tracks: List<Track>,
        val index: Int,
        val station: Boolean,
        val artworkEpoch: Long,
    )

    // Derived metadata only: position ticks must not rehash IDs or encode cover art.
    private var projection: Pair<QueueProjectionKey, List<MediaItemData>>? = null
    var cover: Bitmap? = null
        set(value) {
            field = value
            artworkData = value?.encodedArtwork()
            artworkEpoch++
        }
    fun refresh() = invalidateState()

    override fun getState(): State {
        val snapshot = playback.systemSnapshot()
        val current = snapshot?.state
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_RELEASE,
        )
        if (current != null) {
            commands.addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SET_MEDIA_ITEM,
            )
            if (!current.isStation) {
                commands.addAll(
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SET_REPEAT_MODE,
                    Player.COMMAND_SET_SHUFFLE_MODE,
                )
                if (current.hasNext) {
                    commands.addAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    )
                }
            }
        }
        val builder = State.Builder().setAvailableCommands(commands.build())
        if (snapshot == null || current == null) {
            projection = null
            return builder.build()
        }
        val key =
            QueueProjectionKey(snapshot.mediaId, current.queue, current.currentIndex, current.isStation, artworkEpoch)
        val playlist = projection?.takeIf { it.first == key }?.second ?: current.queue.mapIndexed { index, track ->
            val id = if (index == current.currentIndex) {
                snapshot.mediaId
            } else {
                val bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((snapshot.mediaId + ":" + index + ":" + track.id.value).toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
            }
            val item = MediaItem.Builder().setMediaId(id)
                .setMediaMetadata(
                    mediaMetadata(current.copy(queue = listOf(track), currentIndex = 0), null)
                        .buildUpon().setArtworkData(
                            if (index == current.currentIndex) artworkData else null,
                            MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                        ).build(),
                ).build()
            MediaItemData.Builder(id).setMediaItem(item).setMediaMetadata(item.mediaMetadata)
                .setIsSeekable(!current.isStation)
                .setDurationUs(if (current.isStation) C.TIME_UNSET else track.duration.inWholeMicroseconds).build()
        }
        projection = key to playlist
        return builder.setPlaylist(playlist)
            .setCurrentMediaItemIndex(current.currentIndex)
            .setContentPositionMs(current.positionMs)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(current.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(
                when (current.repeat) {
                    RepeatMode.Off -> Player.REPEAT_MODE_OFF
                    RepeatMode.One -> Player.REPEAT_MODE_ONE
                    RepeatMode.All -> Player.REPEAT_MODE_ALL
                },
            )
            .setShuffleModeEnabled(current.shuffle).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val snapshot = playback.systemSnapshot()
        if (snapshot != null && snapshot.state.isPlaying != playWhenReady) {
            if (!playWhenReady && snapshot.state.isStation) playback.stop() else playback.togglePlayPause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleStop(): ListenableFuture<*> {
        if (playback.systemSnapshot() != null) playback.stop()
        return Futures.immediateVoidFuture()
    }
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetMediaItems(
        items: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val snapshot = playback.systemSnapshot()
        return if (snapshot != null && items.size == 1 && items.single().mediaId == snapshot.mediaId) {
            // Resumption describes the saved queue. Arbitrary controller-supplied sources are never opened.
            Futures.immediateVoidFuture()
        } else {
            Futures.immediateFailedFuture<Void>(SecurityException("Stale playback media ID"))
        }
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val snapshot = playback.systemSnapshot()
        if (snapshot != null && !snapshot.state.isStation) {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> playback.next()
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> playback.previous()
                else -> playback.seekTo(positionMs.coerceAtLeast(0))
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        if (playback.systemSnapshot()?.state?.isStation == false) {
            playback.setRepeat(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    else -> RepeatMode.Off
                },
            )
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        if (playback.systemSnapshot()?.state?.isStation == false) playback.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }
}
