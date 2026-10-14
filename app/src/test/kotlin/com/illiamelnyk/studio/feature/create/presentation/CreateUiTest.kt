package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreateUiTest {

    @Test
    fun `a composing track carries its progress, a queued one none yet`() {
        assertEquals(0.375f, track(TrackStatus.Generating(0.375f)).toCreateUi().progress)
        assertNull(track(TrackStatus.Queued).toCreateUi().progress)
        assertNull(track(TrackStatus.Ready).toCreateUi().progress)
    }

    @Test
    fun `a refused prompt is fixed with the suggestion, or with itself when there is none`() {
        val suggested = track(TrackStatus.Failed(AppError.InvalidPrompt("Pop with an original sound"))).toCreateUi()
        val bare = track(TrackStatus.Failed(AppError.InvalidPrompt(null))).toCreateUi()

        assertEquals(PromptFix("Pop with an original sound", isSuggestion = true), suggested.fix)
        assertEquals(PromptFix("Pop in the style of a band", isSuggestion = false), bare.fix)
        assertEquals(false, suggested.canRetry)
        assertEquals(false, bare.canRetry)
    }

    @Test
    fun `only a track that failed for another reason can be retried`() {
        val failed = track(TrackStatus.Failed(AppError.Server)).toCreateUi()

        assertEquals(true, failed.canRetry)
        assertNull(failed.fix)
        assertEquals(false, track(TrackStatus.Ready).toCreateUi().canRetry)
    }

    private fun track(status: TrackStatus) = Track(
        id = TrackId("a"),
        title = "Pop",
        artist = "Illia",
        prompt = "Pop in the style of a band",
        style = null,
        duration = 2.minutes,
        model = MusicModel.V2_5,
        instrumental = false,
        coverSeed = CoverSeed("a"),
        status = status,
        createdAt = Instant.parse("2026-10-14T09:00:00Z"),
        isLiked = false,
    )
}
