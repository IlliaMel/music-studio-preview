package com.illiamelnyk.studio.core.data.playback

import android.annotation.SuppressLint
import android.content.Context
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal val playbackPersistenceLock = Any()
private val playbackGenerations = MutableStateFlow<Map<String, Long>>(emptyMap())
internal val playbackPurgeGenerations = playbackGenerations.asStateFlow()

internal fun playbackStateGeneration(owner: String): Long = synchronized(playbackPersistenceLock) {
    playbackGenerations.value[owner] ?: 0L
}

// Account metadata is cleared only after this checked durable commit succeeds.
@SuppressLint("UseKtx")
internal fun clearPlaybackState(context: Context, owner: String) {
    synchronized(playbackPersistenceLock) {
        playbackGenerations.value = playbackGenerations.value + (owner to playbackStateGeneration(owner) + 1)
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        if (!preferences.edit().remove(
                playbackPreferenceKey(owner),
            ).remove(autoplayPreferenceKey(owner)).remove("state").commit()
        ) {
            throw IOException("Cannot clear account playback state")
        }
    }
}

internal fun playbackPreferenceKey(owner: String): String = "state-" +
    MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun autoplayPreferenceKey(owner: String): String = "autoplay-" + playbackPreferenceKey(owner)
