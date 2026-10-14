package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.Style
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
        val style = Style("synthwave", "Synthwave", listOf("Retro"), CoverSeed("Synthwave"))
        val settings = GenerationSettings(
            model = MusicModel.V2,
            quality = Quality.Fast,
            instrumental = true,
            duration = 3.minutes,
            style = style,
        )

        val started = generate("  Night drive synthwave \n", settings).value()

        val track = started.single()
        assertEquals(track, tracks.observeMine().first().first())
        assertEquals("Night drive synthwave", track.prompt)
        assertEquals(MusicModel.V2, track.model)
        assertEquals(true, track.instrumental)
        assertEquals(3.minutes, track.duration)
        assertEquals(style, track.style)
    }

    @Test
    fun `two variants start two tracks and spend two credits`() = runTest {
        val (generate) = subject()

        val started = generate("Lo-fi beat", GenerationSettings(variants = 2)).value()

        assertEquals(2, started.size)
        assertEquals(6, credits.credits.first().remaining)
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
    fun `when the credits don't cover every variant, nothing starts`() = runTest {
        val (generate, tracks) = subject()
        repeat(7) { credits.trySpend() }
        val before = tracks.observeMine().first()

        val result = generate("Lo-fi beat", GenerationSettings(variants = 2))

        assertEquals(Outcome.Failure(AppError.QuotaExceeded), result)
        assertEquals(before, tracks.observeMine().first())
        assertEquals(1, credits.credits.first().remaining)
    }

    private fun TestScope.subject(): Pair<GenerateTracks, FakeTrackRepository> {
        val tracks = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)
        return GenerateTracks(tracks, credits) to tracks
    }

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Success).value
}
