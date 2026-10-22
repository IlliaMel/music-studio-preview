package com.illiamelnyk.studio.core.data.playback

import com.illiamelnyk.studio.core.data.demo.DemoLyricsRepository
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricLine
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlinx.coroutines.flow.first

class LocalLyricsRepository(private val tracks: TrackRepository) : LyricsRepository {
    override suspend fun lyrics(id: TrackId): Outcome<List<LyricLine>> {
        val track = tracks.observeById(id).first()
        if (track?.instrumental == true) return Outcome.Success(emptyList())
        if (!track?.words.isNullOrEmpty()) {
            return Outcome.Success(
                track.words.chunked(8).map { words ->
                    LyricLine(words.first().startMs, words.last().endMs, words)
                },
            )
        }
        if (track?.audioUri != null) return Outcome.Success(emptyList())
        val lines = track?.customLyrics?.lines()?.filter { it.isNotBlank() }.orEmpty()
        if (lines.isEmpty()) return DemoLyricsRepository().lyrics(id)
        val lineLength = (track!!.duration.inWholeMilliseconds / lines.size).coerceAtLeast(1)
        return Outcome.Success(
            lines.mapIndexed { index, line ->
                val words = line.trim().split(Regex("\\s+"))
                val start = index * lineLength
                val wordLength = (lineLength / words.size).coerceAtLeast(1)
                LyricLine(
                    start,
                    start + lineLength,
                    words.mapIndexed { wordIndex, text ->
                        LyricWord(text, start + wordIndex * wordLength, start + (wordIndex + 1) * wordLength)
                    },
                )
            },
        )
    }
}
