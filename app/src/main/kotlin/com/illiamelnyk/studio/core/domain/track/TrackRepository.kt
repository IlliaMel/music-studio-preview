package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.flow.Flow

/** The user's own tracks: generating them and keeping them. */
interface TrackRepository {
    /** The user's tracks, newest first. Emits again whenever a track is added, changes or goes. */
    fun observeMine(): Flow<List<Track>>

    /** One of the user's tracks, or `null` once it is deleted. */
    fun observeById(id: TrackId): Flow<Track?>

    /**
     * Starts generating a track. It is added right away as queued or generating and changes
     * status by itself; observe it to follow. Fails at once when, for example, no credits are left.
     */
    suspend fun create(request: GenerationRequest): Outcome<Track> = when (val result = create(request, variants = 1)) {
        is Outcome.Success -> Outcome.Success(result.value.single())
        is Outcome.Failure -> result
    }

    /**
     * Starts one prompt with one or two variants, spending exactly one credit for the whole batch.
     * All variants are added together; quota failure adds none. Results keep variant order.
     * [variants] must be in 1..2.
     */
    suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>>

    /** Generates a failed track again from the same request. */
    suspend fun retry(id: TrackId): Outcome<Track>

    suspend fun delete(id: TrackId): Outcome<Unit>

    suspend fun setLiked(id: TrackId, liked: Boolean): Outcome<Unit>
}
