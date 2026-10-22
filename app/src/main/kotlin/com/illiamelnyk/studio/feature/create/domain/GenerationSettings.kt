package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The lengths a track can be generated at, shortest first. */
object TrackLengths {
    val all: List<Duration> = listOf(
        30.seconds,
        45.seconds,
        1.minutes,
        1.minutes + 30.seconds,
        2.minutes,
        3.minutes,
        4.minutes,
        5.minutes,
    )

    val default: Duration = 2.minutes
}

/** What the user writes for a track: at most [MAX_LENGTH] characters, which the service accepts. */
object Prompt {
    const val MAX_LENGTH = 2_000

    /** Whether [text] can be sent: something besides spaces, and not over the limit. */
    fun isValid(text: String): Boolean = text.trim().length in 10..MAX_LENGTH
}

enum class LyricsMode { Auto, Instrumental, Custom }

/**
 * How the composer generates: the options row and the settings sheet change these, and every track
 * sent from the composer uses them.
 *
 * @param duration one of [TrackLengths.all], or null for the engine to choose.
 * @param variants how many tracks one prompt makes with one credit.
 */
data class GenerationSettings(
    val model: MusicModel = MusicModel.V2_5,
    val quality: Quality = Quality.Auto,
    val lyricsMode: LyricsMode = LyricsMode.Auto,
    val duration: Duration? = null,
    val variants: Int = 1,
    val customLyrics: String = "",
    val referenceAudioUri: String? = null,
    val remixTrackIds: List<String> = emptyList(),
) {

    /** Composition plans use two minutes when Auto is unset; expose that effective length. */
    val effectiveDuration: Duration?
        get() = duration ?: TrackLengths.default.takeIf {
            lyricsMode == LyricsMode.Custom || referenceAudioUri != null || remixTrackIds.isNotEmpty()
        }

    init {
        require(duration == null || duration in TrackLengths.all) { "$duration is not one of the track lengths" }
        require(variants in 1..MAX_VARIANTS) { "Variants must be between 1 and $MAX_VARIANTS, was $variants" }
    }

    companion object {
        const val MAX_VARIANTS = 2
    }
}
