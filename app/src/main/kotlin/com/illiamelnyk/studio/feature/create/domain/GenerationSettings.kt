package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.Style
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
    fun isValid(text: String): Boolean = text.isNotBlank() && text.length <= MAX_LENGTH
}

/**
 * How the composer generates: the options row and the settings sheet change these, and every track
 * sent from the composer uses them.
 *
 * @param duration one of [TrackLengths.all].
 * @param variants how many tracks one prompt makes at once: each is a generation of its own.
 */
data class GenerationSettings(
    val model: MusicModel = MusicModel.V2_5,
    val quality: Quality = Quality.Auto,
    val instrumental: Boolean = false,
    val duration: Duration = TrackLengths.default,
    val style: Style? = null,
    val variants: Int = 1,
) {
    init {
        require(duration in TrackLengths.all) { "$duration is not one of the track lengths" }
        require(variants in 1..MAX_VARIANTS) { "Variants must be between 1 and $MAX_VARIANTS, was $variants" }
    }

    companion object {
        const val MAX_VARIANTS = 2
    }
}
