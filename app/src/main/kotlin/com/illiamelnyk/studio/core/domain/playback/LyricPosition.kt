package com.illiamelnyk.studio.core.domain.playback

/**
 * Where playback is in the lyrics.
 *
 * @param lineIndex the line that started last. It stays current through the pause after it, so
 *   the highlight doesn't jump away between lines.
 * @param wordIndex the word of that line that started last: words up to it have been sung. `null`
 *   while the line has started but its first word hasn't.
 */
data class LyricPosition(val lineIndex: Int, val wordIndex: Int?)

/**
 * The line and word at [positionMs] in [lines], which are sorted by start and don't overlap, as
 * are the words of each line. `null` before the first line starts.
 *
 * It is called on every position update, so it looks the line and the word up by binary search
 * instead of walking the lyrics.
 */
fun currentLyricPosition(lines: List<LyricLine>, positionMs: Long): LyricPosition? {
    val lineIndex = lines.lastStartedAt(positionMs) { it.startMs }
    if (lineIndex < 0) return null
    val wordIndex = lines[lineIndex].words.lastStartedAt(positionMs) { it.startMs }
    return LyricPosition(lineIndex, wordIndex.takeIf { it >= 0 })
}

/** The index of the last element that starts at or before [positionMs], or -1 if none does. */
private inline fun <T> List<T>.lastStartedAt(positionMs: Long, crossinline startMs: (T) -> Long): Int {
    val found = binarySearch { startMs(it).compareTo(positionMs) }
    // Not found: binarySearch returns -(insertion point) - 1, and the element before the insertion
    // point is the last one that has started.
    return if (found >= 0) found else -found - 2
}
