package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.core.domain.AppError

/** Where a track is in its life: waiting, being composed, ready to play, or failed. */
sealed interface TrackStatus {
    /** Accepted, waiting for a free slot. */
    data object Queued : TrackStatus

    /** Being composed; [progress] goes from 0 to 1. */
    data class Generating(val progress: Float) : TrackStatus {
        init {
            require(progress in 0f..1f) { "Progress must be between 0 and 1, was $progress" }
        }
    }

    data object Ready : TrackStatus

    /** Generation failed with [error]; the track stays in the list so it can be retried. */
    data class Failed(val error: AppError) : TrackStatus {
        /** A prompt the service would accept instead, when it rejected this one and offered one. */
        val suggestion: String? get() = (error as? AppError.InvalidPrompt)?.suggestion
    }
}
