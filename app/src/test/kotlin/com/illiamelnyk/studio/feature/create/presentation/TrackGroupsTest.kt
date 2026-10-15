package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.UiText
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackGroupsTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val zone = ZoneId.of("Europe/Chisinau")

    @Test
    fun `same prompt within a minute becomes a pair but never a group of three`() {
        val tracks =
            listOf(
                track("1", "2026-10-15T09:00:40Z"),
                track("2", "2026-10-15T09:00:20Z"),
                track("3", "2026-10-15T09:00:00Z"),
            )
        val rows = tracks.toGroupedCreateUi(today, zone)
        assertEquals(listOf(1, 2, null), rows.map { it.variation })
        assertEquals(listOf(UiText.Resource(R.string.create_today), null, null), rows.map { it.dayLabel })
    }

    @Test
    fun `different prompts old sends and local midnight do not combine`() {
        val tracks = listOf(
            track("1", "2026-10-15T00:00:01Z", "Different"),
            track("2", "2026-10-14T21:00:01Z"),
            track("3", "2026-10-14T20:59:59Z"),
            track("4", "2026-10-14T20:58:00Z"),
        )
        val rows = tracks.toGroupedCreateUi(today, zone)
        assertEquals(listOf(null, null, null, null), rows.map { it.variation })
        assertEquals(UiText.Resource(R.string.create_yesterday), rows[2].dayLabel)
    }

    @Test
    fun `ready subtitles use duration and genres and generating subtitles follow stage`() {
        val song = track("1", "2026-10-15T09:00:00Z").copy(genres = listOf("r&b", "pop"))
        assertEquals(UiText.Raw("0:36 · r&b, pop"), song.toCreateUi().row.subtitle)
        assertEquals(
            UiText.Resource(R.string.create_creating_title),
            song.copy(status = TrackStatus.Generating(0.49f)).toCreateUi().row.subtitle,
        )
        assertEquals(
            UiText.Resource(R.string.create_creating_artwork),
            song.copy(status = TrackStatus.Generating(0.5f)).toCreateUi().row.subtitle,
        )
    }

    @Test
    fun batchIdKeepsVariantOrderAndIndependentSendsSeparate() {
        val base = track("source", "2026-10-15T09:00:00Z")
        val rows = listOf(
            base.copy(id = TrackId("second"), generationId = "batch", variantIndex = 1, variants = 2),
            base.copy(id = TrackId("first"), generationId = "batch", variantIndex = 0, variants = 2),
        ).toGroupedCreateUi(today, zone)
        assertEquals(listOf("first", "second"), rows.map { it.row.id.value })
        assertEquals(listOf(1, 2), rows.map { it.variation })
        val separate = listOf(
            base.copy(generationId = "one"),
            base.copy(id = TrackId("other"), generationId = "two"),
        ).toGroupedCreateUi(today, zone)
        assertEquals(listOf(null, null), separate.map { it.variation })
    }

    private fun track(id: String, created: String, prompt: String = "Lo-fi") = Track(
        TrackId(id), "Title", "Artist", prompt, null, 36.seconds, MusicModel.V2_5,
        false, CoverSeed(id), TrackStatus.Ready, Instant.parse(created), false,
    )
}
