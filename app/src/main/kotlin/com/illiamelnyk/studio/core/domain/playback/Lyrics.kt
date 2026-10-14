package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.TrackId

/** A sung word and when it is sung, in milliseconds from the start of the track. */
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

/** A line of synced lyrics; [words] are in order and lie within the line. */
data class LyricLine(val startMs: Long, val endMs: Long, val words: List<LyricWord>) {
    val text: String get() = words.joinToString(separator = " ") { it.text }
}

/** Synced lyrics of tracks with vocals. */
interface LyricsRepository {
    /** The lines in order, or an empty list for an instrumental or a track without lyrics. */
    suspend fun lyrics(id: TrackId): Outcome<List<LyricLine>>
}
