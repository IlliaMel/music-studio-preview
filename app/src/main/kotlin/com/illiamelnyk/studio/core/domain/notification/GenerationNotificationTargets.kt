package com.illiamelnyk.studio.core.domain.notification

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class GenerationNotificationTarget(val ownerId: String, val trackId: TrackId) {
    companion object {
        const val ACTION = "com.illiamelnyk.studio.OPEN_GENERATION"
        const val OWNER = "generationOwner"
        const val TRACK = "generationTrack"
    }
}

class GenerationNotificationTargets {
    private val mutablePending = MutableStateFlow<GenerationNotificationTarget?>(null)
    val pending = mutablePending.asStateFlow()
    private val mutableFocus = MutableStateFlow<TrackId?>(null)
    val focus = mutableFocus.asStateFlow()

    fun request(target: GenerationNotificationTarget) {
        mutablePending.value = target
    }
    fun consume(target: GenerationNotificationTarget) {
        mutablePending.compareAndSet(target, null)
    }
    fun focus(id: TrackId) {
        mutableFocus.value = id
    }
    fun focused(id: TrackId) {
        mutableFocus.compareAndSet(id, null)
    }
    fun clear() {
        mutablePending.value = null
        mutableFocus.value = null
    }
}

class OpenGenerationNotification(
    private val sessions: SessionRepository,
    private val tracks: TrackRepository,
    private val playback: PlaybackController,
) {
    suspend operator fun invoke(target: GenerationNotificationTarget): Result {
        if (sessions.session.first()?.user?.id?.value != target.ownerId) return Result.Ignored
        val track = tracks.observeById(target.trackId).first() ?: return Result.Ignored
        if (sessions.session.first()?.user?.id?.value != target.ownerId) return Result.Ignored
        return when (track.status) {
            TrackStatus.Ready -> {
                playback.play(listOf(track))
                Result.Player
            }

            is TrackStatus.Failed -> Result.Create

            else -> Result.Ignored
        }
    }

    enum class Result { Player, Create, Ignored }
}
