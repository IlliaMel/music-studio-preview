package com.illiamelnyk.studio.core.data.playback

import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackResumption
import com.illiamelnyk.studio.core.domain.playback.PlaybackState

internal fun mediaMetadata(state: PlaybackState, cover: Bitmap?): MediaMetadata {
    val artwork = cover?.encodedArtwork()
    return MediaMetadata.Builder().setTitle(state.current?.title.orEmpty())
        .setArtist(state.current?.artist.orEmpty()).setIsPlayable(true).setIsBrowsable(false)
        .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
}

internal fun Bitmap.encodedArtwork(): ByteArray = java.io.ByteArrayOutputStream().use { bytes ->
    compress(Bitmap.CompressFormat.PNG, 100, bytes)
    bytes.toByteArray()
}

internal fun recentMediaItem(snapshot: PlaybackResumption, cover: Bitmap?): MediaItem = MediaItem.Builder()
    .setMediaId(snapshot.mediaId).setMediaMetadata(mediaMetadata(snapshot.state, cover)).build()

internal fun recentRoot(snapshot: PlaybackResumption): MediaItem = MediaItem.Builder()
    .setMediaId("recent:" + snapshot.mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setTitle("Recent").build(),
    ).build()

internal fun resumeSystemPlayback(playback: PlaybackController, mediaId: String?): Boolean {
    val snapshot = playback.systemSnapshot() ?: return false
    if (mediaId != null && mediaId != snapshot.mediaId) return false
    if (!snapshot.state.isPlaying) playback.togglePlayPause()
    return true
}
