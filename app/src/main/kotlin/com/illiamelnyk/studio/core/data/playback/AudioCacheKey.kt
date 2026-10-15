package com.illiamelnyk.studio.core.data.playback

import com.illiamelnyk.studio.core.domain.track.TrackId
import java.security.MessageDigest

internal fun audioCacheKey(id: TrackId): String = MessageDigest.getInstance("SHA-256")
    .digest(id.value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun audioCacheKey(id: TrackId, ownerId: String): String = audioCacheKey(id) + "-" +
    MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
