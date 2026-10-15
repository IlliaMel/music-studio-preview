package com.illiamelnyk.studio.testing

import com.illiamelnyk.studio.core.data.fake.FakeCatalog
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
    initial: List<Track> = FakeCatalog.tracks.map {
        it.copy(isLiked = false)
    },
) : TrackRepository {
    private val tracks = MutableStateFlow(initial)
    var failLike = false
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
