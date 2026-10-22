package com.illiamelnyk.studio.testing

import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MemoryTrackRepository(
    initial: List<Track> = DemoCatalog.tracks.map {
        it.copy(isLiked = false)
    },
) : TrackRepository {
    private val tracks = MutableStateFlow(initial)
    var failLike = false
    override suspend fun saveRecording(
        localUri: String,
        title: String,
        duration: kotlin.time.Duration,
    ): Outcome<Track> = Outcome.Failure(AppError.UnsupportedMode)
    override suspend fun prepareRecordingReference(
        id: TrackId,
    ): Outcome<com.illiamelnyk.studio.core.domain.track.RecordingReference> = Outcome.Failure(AppError.InvalidReference)
    override fun isRecordingReferenceCurrent(
        reference: com.illiamelnyk.studio.core.domain.track.RecordingReference,
    ): Boolean = false
    override suspend fun rename(id: TrackId, title: String): Outcome<Unit> = Outcome.Failure(AppError.UnsupportedMode)

    override fun observeMine(): Flow<List<Track>> = tracks
    override fun observeById(id: TrackId): Flow<Track?> = tracks.map { list -> list.find { it.id == id } }
    override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> =
        Outcome.Failure(AppError.Unknown)
    override suspend fun retry(id: TrackId): Outcome<Track> = Outcome.Failure(AppError.Unknown)
    override suspend fun delete(id: TrackId): Outcome<Unit> = Outcome.Failure(AppError.Unknown)
    override suspend fun setLiked(id: TrackId, liked: Boolean): Outcome<Unit> {
        if (failLike) return Outcome.Failure(AppError.Network)
        tracks.value = tracks.value.map { if (it.id == id) it.copy(isLiked = liked) else it }
        return Outcome.Success(Unit)
    }
}
