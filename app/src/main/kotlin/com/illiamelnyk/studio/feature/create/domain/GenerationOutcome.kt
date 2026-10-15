package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackStatus

/** A generation that has just ended, one way or the other. */
sealed interface GenerationOutcome {
    val track: Track

    data class Ready(override val track: Track) : GenerationOutcome

    data class Failed(override val track: Track, val error: AppError) : GenerationOutcome
}

/**
 * The generations that ended between two lists of the user's tracks: tracks that were queued or
 * composing in [before] and are ready or failed in [after], in the order of [after]. A track that
 * shows up already finished, such as one loaded with the list, isn't one: it didn't end just now.
 */
fun endedGenerations(before: List<Track>, after: List<Track>): List<GenerationOutcome> {
    val inProgress = before.filter { it.status.isInProgress }.mapTo(HashSet()) { it.id }
    if (inProgress.isEmpty()) return emptyList()
    return after.mapNotNull { track ->
        if (track.id !in inProgress) return@mapNotNull null
        when (val status = track.status) {
            TrackStatus.Ready -> GenerationOutcome.Ready(track)
            is TrackStatus.Failed -> GenerationOutcome.Failed(track, status.error)
            TrackStatus.Queued, is TrackStatus.Generating -> null
        }
    }
}

private val TrackStatus.isInProgress: Boolean
    get() = this == TrackStatus.Queued || this is TrackStatus.Generating
