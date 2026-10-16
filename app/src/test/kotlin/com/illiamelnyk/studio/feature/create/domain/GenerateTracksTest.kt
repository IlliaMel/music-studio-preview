package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateTracksTest {

    private val credits = FakeCreditsRepository()
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-14T09:00:00Z")
    }

    @Test
    fun `a prompt is sent trimmed, with every setting`() = runTest {
        val (generate, tracks) = subject()
        val settings = GenerationSettings(
            model = MusicModel.V2,
            quality = Quality.Fast,
            lyricsMode = LyricsMode.Instrumental,
            duration = 3.minutes,
        )

        val started = generate("  Night drive synthwave \n", settings).value()

        val track = started.single()
        assertEquals(track, tracks.observeMine().first().first())
        assertEquals("Night drive synthwave", track.prompt)
        assertEquals(MusicModel.V2, track.model)
        assertEquals(true, track.instrumental)
        assertEquals(3.minutes, track.duration)
    }

    @Test
    fun `two variants start two tracks and spend one credit`() = runTest {
        val (generate) = subject()

        val started = generate("Lo-fi beat", GenerationSettings(variants = 2)).value()

        assertEquals(2, started.size)
        assertEquals(7, credits.credits.first().remaining)
    }

    @Test
    fun `a blank prompt or one over the limit starts nothing`() = runTest {
        val (generate, tracks) = subject()
        val before = tracks.observeMine().first()

        assertEquals(Outcome.Failure(AppError.InvalidPrompt(null)), generate(" \n ", GenerationSettings()))
        assertEquals(
            Outcome.Failure(AppError.InvalidPrompt(null)),
            generate("a".repeat(Prompt.MAX_LENGTH + 1), GenerationSettings()),
        )
        assertEquals(before, tracks.observeMine().first())
        assertEquals(8, credits.credits.first().remaining)
    }

    @Test
    fun `a prompt of exactly the limit is sent`() = runTest {
        val (generate) = subject()

        val result = generate("a".repeat(Prompt.MAX_LENGTH), GenerationSettings())

        assertEquals(1, result.value().size)
    }

    @Test
    fun `without credits nothing starts`() = runTest {
        val (generate, tracks) = subject()
        repeat(8) { credits.trySpend() }
        val before = tracks.observeMine().first()

        val result = generate("Lo-fi beat", GenerationSettings(variants = 2))

        assertEquals(Outcome.Failure(AppError.QuotaExceeded), result)
        assertEquals(before, tracks.observeMine().first())
        assertEquals(0, credits.credits.first().remaining)
    }

    @Test
    fun customLyricsAndRecordingReferenceReachDataContract() = runTest {
        val tracks = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        var captured: com.illiamelnyk.studio.core.domain.track.GenerationRequest? = null
        val repository = object : com.illiamelnyk.studio.core.domain.track.TrackRepository by tracks {
            override suspend fun create(
                request: com.illiamelnyk.studio.core.domain.track.GenerationRequest,
                variants: Int,
            ): Outcome<List<com.illiamelnyk.studio.core.domain.track.Track>> {
                captured = request
                return tracks.create(request, variants)
            }
        }
        val generate = GenerateTracks(repository, credits)
        val result = generate(
            "Original acoustic melody",
            GenerationSettings(
                lyricsMode = LyricsMode.Custom,
                customLyrics = "A new day\nA new song",
                referenceAudioUri = "file:///reference.m4a",
            ),
        )
        assertEquals(true, result is Outcome.Success)
        assertEquals("A new day\nA new song", captured?.customLyrics)
        assertEquals("file:///reference.m4a", captured?.referenceAudioUri)
        assertEquals(emptyList<String>(), captured?.remixSourceIds)
        assertEquals(false, captured?.instrumental)
        assertEquals(7, credits.credits.first().remaining)
    }

    @Test
    fun customLyricsAndOrderedRemixSourcesReachDataContract() = runTest {
        val tracks = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        var captured: com.illiamelnyk.studio.core.domain.track.GenerationRequest? = null
        val repository = object : com.illiamelnyk.studio.core.domain.track.TrackRepository by tracks {
            override suspend fun create(
                request: com.illiamelnyk.studio.core.domain.track.GenerationRequest,
                variants: Int,
            ): Outcome<List<com.illiamelnyk.studio.core.domain.track.Track>> {
                captured = request
                return tracks.create(request, variants)
            }
        }
        val sources = listOf("source-second", "source-first")
        val result = GenerateTracks(repository, credits)(
            "Blend two original acoustic ideas",
            GenerationSettings(
                lyricsMode = LyricsMode.Custom,
                customLyrics = "A new day\nA new song",
                remixTrackIds = sources,
            ),
        )
        assertEquals(true, result is Outcome.Success)
        assertEquals(sources, captured?.remixTrackIds)
        assertEquals("source-second", captured?.remixTrackId)
        assertEquals(null, captured?.referenceAudioUri)
        assertEquals("A new day\nA new song", captured?.customLyrics)
        assertEquals(7, credits.credits.first().remaining)
    }

    @Test
    fun combinedRecordingAndRemixReferencesFailBeforeAnyRepositoryMutationOrCredit() = runTest {
        val tracks = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        var mutations = 0
        val repository = object : com.illiamelnyk.studio.core.domain.track.TrackRepository by tracks {
            override suspend fun create(
                request: com.illiamelnyk.studio.core.domain.track.GenerationRequest,
                variants: Int,
            ): Outcome<List<com.illiamelnyk.studio.core.domain.track.Track>> {
                mutations++
                return tracks.create(request, variants)
            }
        }
        val before = tracks.observeMine().first()
        val result = GenerateTracks(repository, credits)(
            "Original acoustic melody",
            GenerationSettings(
                referenceAudioUri = "file:///reference.m4a",
                remixTrackIds = listOf("source-first", "source-second"),
            ),
        )
        assertEquals(Outcome.Failure(AppError.InvalidReference), result)
        assertEquals(0, mutations)
        assertEquals(before, tracks.observeMine().first())
        assertEquals(8, credits.credits.first().remaining)
    }

    @Test
    fun invalidLyricsAndShortPromptSpendNothing() = runTest {
        val (generate, tracks) = subject()
        val before = tracks.observeMine().first()
        assertEquals(
            Outcome.Failure(AppError.InvalidPrompt(null)),
            generate("A full music prompt", GenerationSettings(lyricsMode = LyricsMode.Custom)),
        )
        assertEquals(Outcome.Failure(AppError.InvalidPrompt(null)), generate("short", GenerationSettings()))
        assertEquals(before, tracks.observeMine().first())
        assertEquals(8, credits.credits.first().remaining)
    }

    @Test
    fun rawRecordingRemixIdIsRejectedBeforeRepositoryMutationAndCredit() = runTest {
        val tracks = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        var mutations = 0
        val repository = object : com.illiamelnyk.studio.core.domain.track.TrackRepository by tracks {
            override suspend fun create(
                request: com.illiamelnyk.studio.core.domain.track.GenerationRequest,
                variants: Int,
            ): Outcome<List<com.illiamelnyk.studio.core.domain.track.Track>> {
                mutations++
                return tracks.create(request, variants)
            }
        }
        val before = tracks.observeMine().first()
        val result = GenerateTracks(repository, credits)(
            "A recording cannot be a remix original",
            GenerationSettings(remixTrackIds = listOf("recording-8ced07a1-2964-4a3f-aa80-75177020463b")),
        )
        assertEquals(Outcome.Failure(AppError.InvalidReference), result)
        assertEquals(0, mutations)
        assertEquals(before, tracks.observeMine().first())
        assertEquals(8, credits.credits.first().remaining)
    }

    private fun TestScope.subject(): Pair<GenerateTracks, FakeTrackRepository> {
        val tracks = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        return GenerateTracks(tracks, credits) to tracks
    }

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Success).value
}
