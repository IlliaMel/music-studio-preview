package com.illiamelnyk.studio.core.data.playback

import android.annotation.SuppressLint
import android.content.Context
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal val playbackPersistenceLock = Any()
internal val playbackRestoreLock = Any()
private val blockedPlaybackOwners = mutableSetOf<String>()
private val playbackGenerations = MutableStateFlow<Map<String, Long>>(emptyMap())
internal val playbackPurgeGenerations = playbackGenerations.asStateFlow()

internal fun playbackStateGeneration(owner: String): Long = playbackGenerations.value[owner] ?: 0L

internal fun playbackRestoreGeneration(owner: String): Long? = synchronized(playbackRestoreLock) {
    playbackStateGeneration(owner).takeUnless { owner in blockedPlaybackOwners }
}

// Account metadata is cleared only after this checked durable commit succeeds.
@SuppressLint("UseKtx")
internal fun clearPlaybackState(context: Context, owner: String) {
    synchronized(playbackPersistenceLock) {
        synchronized(playbackRestoreLock) { blockedPlaybackOwners.add(owner) }
        playbackGenerations.value = playbackGenerations.value + (owner to playbackStateGeneration(owner) + 1)
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        if (!preferences.edit().remove(
                playbackPreferenceKey(owner),
            ).remove(autoplayPreferenceKey(owner)).remove("state").commit()
        ) {
            throw IOException("Cannot clear account playback state")
        }
        synchronized(playbackRestoreLock) { blockedPlaybackOwners.remove(owner) }
    }
}

internal fun playbackPreferenceKey(owner: String): String = "state-" +
    MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun autoplayPreferenceKey(owner: String): String = "autoplay-" + playbackPreferenceKey(owner)
