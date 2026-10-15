package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId

/**
 * The demo catalog: tracks other people "made", and synced lyrics for two of them. Titles and
 * artists are made up.
 *
 * Temporary: replaced by the backend-backed implementation, whose catalog is `GET /v1/catalog`.
 */
object FakeCatalog {
    val tracks: List<Track> = PUBLIC_CATALOG_TRACKS
    val glassHarbor: Track = tracks.first { it.id.value == "glass-harbor" }
    val paperMoons: Track = tracks.first { it.id.value == "paper-moons" }

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
