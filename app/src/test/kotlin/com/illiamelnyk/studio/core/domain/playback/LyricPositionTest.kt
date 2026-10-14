package com.illiamelnyk.studio.core.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricPositionTest {

    // Line 0: "one" 1000–1500, "two" 1500–2000. A pause. Line 1 starts at 3000, its first word at 3200.
    private val lines = listOf(
        LyricLine(1_000, 2_000, listOf(LyricWord("one", 1_000, 1_500), LyricWord("two", 1_500, 2_000))),
        LyricLine(3_000, 4_000, listOf(LyricWord("three", 3_200, 3_600), LyricWord("four", 3_600, 4_000))),
    )

    @Test
    fun `the position follows the lines and words`() {
        val expected = mapOf(
            -500L to null, // before the track starts, after seeking back past zero
            0L to null,
            999L to null, // just before the first line
            1_000L to LyricPosition(lineIndex = 0, wordIndex = 0), // exactly at the start
            1_499L to LyricPosition(lineIndex = 0, wordIndex = 0),
            1_500L to LyricPosition(lineIndex = 0, wordIndex = 1), // the second word starts
            2_500L to LyricPosition(lineIndex = 0, wordIndex = 1), // between lines: the first stays current
            3_000L to LyricPosition(lineIndex = 1, wordIndex = null), // the line starts before its first word
            3_200L to LyricPosition(lineIndex = 1, wordIndex = 0),
            3_700L to LyricPosition(lineIndex = 1, wordIndex = 1), // inside the last word
            600_000L to LyricPosition(lineIndex = 1, wordIndex = 1), // long after the last line
        )

        expected.forEach { (positionMs, position) ->
            assertEquals("at $positionMs ms", position, currentLyricPosition(lines, positionMs))
        }
    }

    @Test
    fun `a long song is searched correctly at every line start`() {
        val longSong = List(1_000) { index ->
            val start = index * 4_000L
            LyricLine(start, start + 3_000, listOf(LyricWord("la", start, start + 3_000)))
        }

        listOf(0, 1, 499, 500, 998, 999).forEach { index ->
            assertEquals(LyricPosition(index, 0), currentLyricPosition(longSong, index * 4_000L))
            assertEquals(LyricPosition(index, 0), currentLyricPosition(longSong, index * 4_000L + 3_999))
        }
    }

    @Test
    fun `no lyrics means no position`() {
        assertEquals(null, currentLyricPosition(emptyList(), 1_000))
    }
}
