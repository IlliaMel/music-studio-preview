package com.illiamelnyk.studio.core.domain.notification

import com.illiamelnyk.studio.core.domain.track.Track

interface GenerationNotificationPreferences {
    suspend fun hasAnswered(): Boolean
    suspend fun answer(enabled: Boolean)
    suspend fun isEnabled(): Boolean
}

interface GenerationNotifications {
    fun record(ownerId: String?, tracks: List<Track>)
    fun recover(ownerId: String?, tracks: List<Track>, observedIds: Set<String>) = record(ownerId, tracks)
    fun hasPending(ownerId: String): Boolean = false
    suspend fun deliver(ownerId: String?)
    fun forget(ownerId: String)

    data object None : GenerationNotifications {
        override fun record(ownerId: String?, tracks: List<Track>) = Unit
        override suspend fun deliver(ownerId: String?) = Unit
        override fun forget(ownerId: String) = Unit
    }
}
