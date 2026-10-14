package com.illiamelnyk.studio.testing

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository

/**
 * [delegate], except that while [failWith] is set every call that changes something fails with
 * it, the way the backend answers when the network drops.
 */
class FailingTrackRepository(private val delegate: TrackRepository) : TrackRepository by delegate {
    var failWith: AppError? = null

    override suspend fun create(request: GenerationRequest): Outcome<Track> = failure() ?: delegate.create(request)

    override suspend fun retry(id: TrackId): Outcome<Track> = failure() ?: delegate.retry(id)

    override suspend fun delete(id: TrackId): Outcome<Unit> = failure() ?: delegate.delete(id)

    override suspend fun setLiked(id: TrackId, liked: Boolean): Outcome<Unit> =
        failure() ?: delegate.setLiked(id, liked)

    private fun failure(): Outcome.Failure? = failWith?.let { Outcome.Failure(it) }
}
