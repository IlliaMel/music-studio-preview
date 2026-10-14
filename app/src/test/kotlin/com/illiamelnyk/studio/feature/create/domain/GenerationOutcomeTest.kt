package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationOutcomeTest {

    @Test
    fun `a composing track that is ready now has ended`() {
        val before = listOf(track("a", TrackStatus.Generating(0.5f)), track("b", TrackStatus.Ready))
        val after = listOf(track("a", TrackStatus.Ready), track("b", TrackStatus.Ready))

        assertEquals(listOf(GenerationOutcome.Ready(after[0])), endedGenerations(before, after))
    }

    @Test
    fun `a queued track that failed has ended with its error`() {
        val error = AppError.InvalidPrompt("Pop with an original sound")
        val after = listOf(track("a", TrackStatus.Failed(error)))

        assertEquals(
            listOf(GenerationOutcome.Failed(after[0], error)),
            endedGenerations(before = listOf(track("a", TrackStatus.Queued)), after = after),
        )
    }

    @Test
    fun `tracks that were already finished, or are still composing, haven't ended now`() {
        val before = listOf(
            track("ready", TrackStatus.Ready),
            track("failed", TrackStatus.Failed(AppError.Server)),
            track("composing", TrackStatus.Generating(0.25f)),
        )
        val after = listOf(
            track("ready", TrackStatus.Ready),
            track("failed", TrackStatus.Failed(AppError.Server)),
            track("composing", TrackStatus.Generating(0.5f)),
        )

        assertEquals(emptyList<GenerationOutcome>(), endedGenerations(before, after))
    }

    @Test
    fun `the first list of tracks announces nothing, finished or not`() {
        val after = listOf(track("a", TrackStatus.Ready), track("b", TrackStatus.Generating(1f)))

        assertEquals(emptyList<GenerationOutcome>(), endedGenerations(before = emptyList(), after = after))
    }

    @Test
    fun `a composing track that was deleted hasn't ended`() {
        val before = listOf(track("a", TrackStatus.Generating(0.5f)))

        assertEquals(emptyList<GenerationOutcome>(), endedGenerations(before, after = emptyList()))
    }

    private fun track(id: String, status: TrackStatus) = Track(
        id = TrackId(id),
        title = "Track $id",
        artist = "Illia",
        prompt = "Prompt $id",
        style = null,
        duration = 2.minutes,
        model = MusicModel.V2_5,
        instrumental = false,
        coverSeed = CoverSeed(id),
        status = status,
        createdAt = Instant.parse("2026-10-14T09:00:00Z"),
        isLiked = false,
    )
}
