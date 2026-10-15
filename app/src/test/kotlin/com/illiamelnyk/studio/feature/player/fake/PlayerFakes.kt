package com.illiamelnyk.studio.feature.player.fake

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Style
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** How long the fakes take to answer, like the network would. */
val FAKE_LATENCY = 300.milliseconds

/**
 * The user's tracks in memory, for the player: it keeps likes and can fail the next one. Like the
 * app's fake, it only knows the user's own tracks, not the catalog's.
 */
class InMemoryTrackRepository(mine: List<Track> = emptyList()) : TrackRepository {
    private val tracks = MutableStateFlow(mine)

    /** The error the next [setLiked] fails with; cleared once used. */
    var nextLikeFailure: AppError? = null

    override fun observeMine(): Flow<List<Track>> = tracks

    override fun observeById(id: TrackId): Flow<Track?> = tracks.map { list ->
        list.find { it.id == id }
    }.distinctUntilChanged()

    override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> =
        Outcome.Failure(AppError.Unknown)

    override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> =
        Outcome.Failure(AppError.Unknown)

    override suspend fun retry(id: TrackId): Outcome<Track> = Outcome.Failure(AppError.Unknown)

    override suspend fun delete(id: TrackId): Outcome<Unit> = Outcome.Failure(AppError.Unknown)

    override suspend fun setLiked(id: TrackId, liked: Boolean): Outcome<Unit> {
        delay(FAKE_LATENCY)
        nextLikeFailure?.let { error ->
            nextLikeFailure = null
            return Outcome.Failure(error)
        }
        tracks.update { list -> list.map { if (it.id == id) it.copy(isLiked = liked) else it } }
        return Outcome.Success(Unit)
    }
}

/** Lyrics by track, answering after [latency]; a track can be made to fail. */
class InMemoryLyricsRepository(private val lyrics: Map<TrackId, List<LyricLine>> = emptyMap()) : LyricsRepository {
    val failing = mutableSetOf<TrackId>()
    val requested = mutableListOf<TrackId>()
    var latency: Duration = FAKE_LATENCY

    override suspend fun lyrics(id: TrackId): Outcome<List<LyricLine>> {
        requested += id
        delay(latency)
        if (id in failing) return Outcome.Failure(AppError.Network)
        return Outcome.Success(lyrics[id].orEmpty())
    }
}

fun track(
    id: String,
    duration: Duration,
    instrumental: Boolean = false,
    liked: Boolean = false,
    prompt: String = "",
    style: Style? = null,
) = Track(
    id = TrackId(id),
    title = "Title $id",
    artist = "Artist $id",
    prompt = prompt,
    style = style,
    duration = duration,
    model = MusicModel.V2_5,
    instrumental = instrumental,
    coverSeed = CoverSeed(id),
    status = TrackStatus.Ready,
    createdAt = Instant.parse("2026-10-14T09:00:00Z"),
    isLiked = liked,
)

/** A line whose words start one after another, [wordMs] apart. */
fun lyricLine(startMs: Long, text: String, wordMs: Long = 500): LyricLine {
    val words = text.split(' ').mapIndexed { index, word ->
        LyricWord(word, startMs + index * wordMs, startMs + (index + 1) * wordMs)
    }
    return LyricLine(startMs, words.last().endMs, words)
}
