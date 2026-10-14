package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTrackRepositoryTest {

    @Test
    fun `liking a catalog track adds it to liked without adding it to creations`() = runTest {
        val repository = repository()
        val catalogTrack = FakeCatalog.tracks.first()
        val mine = repository.observeMine().first()

        repository.setLiked(catalogTrack.id, true)

        assertEquals(mine, repository.observeMine().first())
        assertTrue(repository.observeLiked().first().any { it.id == catalogTrack.id && it.isLiked })
        assertEquals(true, repository.observeById(catalogTrack.id).first()?.isLiked)

        repository.setLiked(catalogTrack.id, false)

        assertTrue(repository.observeLiked().first().none { it.id == catalogTrack.id })
        assertEquals(false, repository.observeById(catalogTrack.id).first()?.isLiked)
    }

    @Test
    fun `liking an unknown track reports failure`() = runTest {
        assertEquals(Outcome.Failure(AppError.Unknown), repository().setLiked(TrackId("missing"), true))
    }

    private val credits = FakeCreditsRepository()
    private val now = Instant.parse("2026-10-14T09:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@FakeTrackRepositoryTest.now
    }

    @Test
    fun `a new track generates for 8 seconds and becomes ready`() = runTest {
        val repository = repository()

        val track = repository.create(GenerationRequest("Lo-fi beat for studying", 2.minutes)).value()
        assertEquals(TrackStatus.Generating(progress = 0f), track.status)
        assertEquals(track, repository.observeMine().first().first()) // added on top right away
        assertEquals(now, track.createdAt)

        advanceTimeBy(8.seconds - 1.milliseconds)
        assertEquals(TrackStatus.Generating(progress = 0.875f), repository.statusOf(track.id))

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(TrackStatus.Ready, repository.statusOf(track.id))
    }

    @Test
    fun `a new track is titled with the first whole words of its prompt`() = runTest {
        val repository = repository()

        val long = repository.create(
            GenerationRequest("dreamy synth-pop about a night train, warm pads and a catchy chorus", 2.minutes),
        ).value()
        val short = repository.create(GenerationRequest("  lo-fi beat  ", 2.minutes)).value()

        assertEquals("Dreamy synth-pop about a night train", long.title)
        assertEquals("Lo-fi beat", short.title)
    }

    @Test
    fun `a prompt that names an artist fails with a suggestion`() = runTest {
        val repository = repository()

        val track = repository.create(
            GenerationRequest("Dreamy pop in the style of Some Band, with strings", 2.minutes),
        ).value()
        advanceTimeBy(2.seconds - 1.milliseconds)
        assertEquals(TrackStatus.Generating(progress = 0f), repository.statusOf(track.id))

        advanceTimeBy(1.milliseconds)
        runCurrent()
        val expected = TrackStatus.Failed(
            AppError.InvalidPrompt("Dreamy pop with an original sound of its own, with strings"),
        )
        assertEquals(expected, repository.statusOf(track.id))

        // It stays failed instead of turning ready later on.
        advanceTimeBy(1.minutes)
        assertEquals(expected, repository.statusOf(track.id))
    }

    @Test
    fun `every generation spends a credit`() = runTest {
        val repository = repository()

        repository.create(GenerationRequest("First", 1.minutes))
        repository.create(GenerationRequest("Second", 1.minutes))

        assertEquals(Credits(remaining = 6, dailyLimit = 8), credits.credits.first())
    }

    @Test
    fun `without credits nothing is generated`() = runTest {
        val repository = repository()
        repeat(8) { repository.create(GenerationRequest("Track $it", 1.minutes)) }
        val before = repository.observeMine().first().map { it.id }

        val result = repository.create(GenerationRequest("One too many", 1.minutes))

        assertEquals(Outcome.Failure(AppError.QuotaExceeded), result)
        assertEquals(before, repository.observeMine().first().map { it.id })
    }

    @Test
    fun `retry generates a failed track again, once however often it is tapped`() = runTest {
        val repository = repository()
        val failed = TrackId("seed-5") // failed with a server error in the seed

        repository.retry(failed)
        repository.retry(failed)
        assertEquals(TrackStatus.Generating(progress = 0f), repository.statusOf(failed))
        assertEquals(7, credits.credits.first().remaining)

        advanceTimeBy(8.seconds)
        assertEquals(TrackStatus.Ready, repository.statusOf(failed))
    }

    @Test
    fun `a deleted track stops generating and does not come back`() = runTest {
        val repository = repository()
        val track = repository.create(GenerationRequest("Short-lived", 1.minutes)).value()

        advanceTimeBy(3.seconds)
        repository.delete(track.id)
        advanceTimeBy(1.minutes)

        assertEquals(null, repository.observeById(track.id).first())
    }

    @Test
    fun `two variants share the last credit and both finish generating`() = runTest {
        val repository = repository()
        repeat(7) { credits.trySpend() }

        val batch = (repository.create(GenerationRequest("Ambient dawn"), variants = 2) as Outcome.Success).value

        assertEquals(2, batch.size)
        assertNotEquals(batch[0].id, batch[1].id)
        assertNotEquals(batch[0].coverSeed, batch[1].coverSeed)
        assertEquals(listOf(now, now), batch.map { it.createdAt })
        assertEquals(batch, repository.observeMine().first().take(2))
        assertEquals(0, credits.credits.first().remaining)
        advanceTimeBy(8.seconds)
        runCurrent()
        assertEquals(listOf(TrackStatus.Ready, TrackStatus.Ready), batch.map { repository.statusOf(it.id) })
    }

    @Test
    fun `batch without credits adds no partial variants`() = runTest {
        val repository = repository()
        repeat(8) { credits.trySpend() }
        val before = repository.observeMine().first()

        val result = repository.create(GenerationRequest("Ambient dawn"), variants = 2)

        assertEquals(Outcome.Failure(AppError.QuotaExceeded), result)
        assertEquals(before, repository.observeMine().first())
    }

    @Test
    fun `concurrent batches cannot both spend the final credit`() = runTest {
        val repository = repository()
        repeat(7) { credits.trySpend() }
        val initialSize = repository.observeMine().first().size

        val results = List(2) {
            async { repository.create(GenerationRequest("Prompt $it"), variants = 2) }
        }.awaitAll()

        assertEquals(1, results.count { it is Outcome.Success })
        assertEquals(1, results.count { it == Outcome.Failure(AppError.QuotaExceeded) })
        assertEquals(initialSize + 2, repository.observeMine().first().size)
        assertEquals(0, credits.credits.first().remaining)
    }

    @Test
    fun `Auto chooses a playable duration while explicit length is preserved`() = runTest {
        val repository = repository()

        val auto = repository.create(GenerationRequest("Auto length")).value()
        val explicit = repository.create(GenerationRequest("Short song", 30.seconds)).value()

        assertEquals(2.minutes, auto.duration)
        assertEquals(30.seconds, explicit.duration)
        assertTrue(auto.genres.isEmpty())
    }

    @Test
    fun `unsupported variant counts cannot spend credits or add tracks`() = runTest {
        val repository = repository()
        val before = repository.observeMine().first()

        for (variants in listOf(0, 3)) {
            val failure = runCatching { repository.create(GenerationRequest("Invalid batch"), variants) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }

        assertEquals(before, repository.observeMine().first())
        assertEquals(8, credits.credits.first().remaining)
    }

    private fun TestScope.repository() = FakeTrackRepository(credits, FakeSessionRepository(), backgroundScope, clock)

    private suspend fun FakeTrackRepository.statusOf(id: TrackId): TrackStatus? = observeById(id).first()?.status

    private fun Outcome<Track>.value(): Track = (this as Outcome.Success).value
}
