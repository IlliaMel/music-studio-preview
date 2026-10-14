package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The demo catalog: tracks other people "made", and synced lyrics for two of them. Titles and
 * artists are made up.
 *
 * Temporary: replaced by the backend-backed implementation, whose catalog is `GET /v1/catalog`.
 */
object FakeCatalog {
    val glassHarbor = catalogTrack("glass-harbor", "Glass Harbor", "Mira Vale", 3.minutes + 12.seconds)
    val paperMoons = catalogTrack("paper-moons", "Paper Moons", "Ada Kowal", 3.minutes + 30.seconds)

    val tracks: List<Track> = listOf(
        glassHarbor,
        catalogTrack("night-drive", "Night Drive Through Kyiv", "Lumen Drift", 2.minutes + 48.seconds, true),
        paperMoons,
        catalogTrack("cold-brew", "Cold Brew Mornings", "Hollow Pines", 2.minutes + 5.seconds, true),
        catalogTrack("signal-fire", "Signal Fire", "North Room", 3.minutes + 41.seconds),
        catalogTrack("copper-sky", "Copper Sky", "Velvet Static", 4.minutes + 2.seconds),
        catalogTrack("lanterns", "Lanterns on the Vistula", "Sasha Ivo", 2.minutes + 56.seconds),
        catalogTrack("static-bloom", "Static Bloom", "Taras Hai", 3.minutes + 18.seconds, true),
        catalogTrack("afterglow", "Afterglow Avenue", "Mira Vale", 3.minutes + 5.seconds),
        catalogTrack("slow-tide", "Slow Tide", "Hollow Pines", 4.minutes + 20.seconds, true),
        catalogTrack("kinetic", "Kinetic", "North Room", 2.minutes + 30.seconds, true),
        catalogTrack("low-orbit", "Low Orbit", "Lumen Drift", 3.minutes + 50.seconds, true),
    )

    val lyrics: Map<TrackId, List<LyricLine>> = mapOf(
        glassHarbor.id to listOf(
            line(12_000, 16_000, "Lights are low on the glass harbor"),
            line(16_500, 20_500, "Boats asleep in the evening haze"),
            line(21_000, 25_000, "I keep your voice in a paper lantern"),
            line(25_500, 30_000, "Burning slow through the winter days"),
            line(36_000, 40_000, "Hold on, hold on to the water"),
            line(40_500, 45_000, "Every wave is a way back home"),
        ),
        paperMoons.id to listOf(
            line(9_000, 12_500, "Paper moons on the bedroom ceiling"),
            line(13_000, 16_500, "Cut them out when the night was long"),
            line(17_000, 21_000, "Every crease is a word I'm keeping"),
            line(21_500, 25_000, "Folded up in a quiet song"),
            line(31_000, 34_000, "Shine for me, shine for me"),
            line(34_500, 38_500, "Even paper can learn to glow"),
        ),
    )

    private fun catalogTrack(
        id: String,
        title: String,
        artist: String,
        duration: Duration,
        instrumental: Boolean = false,
    ) = Track(
        id = TrackId(id),
        title = title,
        artist = artist,
        prompt = "",
        style = null,
        duration = duration,
        model = MusicModel.V2_5,
        instrumental = instrumental,
        coverSeed = CoverSeed(title),
        status = TrackStatus.Ready,
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        isLiked = false,
    )

    /** A line whose words share its time evenly: close enough to singing for a demo. */
    private fun line(startMs: Long, endMs: Long, text: String): LyricLine {
        val words = text.split(' ')
        val wordMs = (endMs - startMs) / words.size
        return LyricLine(
            startMs = startMs,
            endMs = endMs,
            words = words.mapIndexed { index, word ->
                LyricWord(word, startMs = startMs + index * wordMs, endMs = startMs + (index + 1) * wordMs)
            },
        )
    }
}
