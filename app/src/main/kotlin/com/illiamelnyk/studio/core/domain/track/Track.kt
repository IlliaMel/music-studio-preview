package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import kotlin.time.Duration
import kotlin.time.Instant

@JvmInline
value class TrackId(val value: String)

/**
 * A song: one the user generated, or one from the catalog.
 *
 * @param artist who made it: the author's public name.
 * @param prompt the description it was generated from.
 * @param instrumental generated without vocals.
 */
data class Track(
    val id: TrackId,
    val title: String,
    val artist: String,
    val prompt: String,
    val style: Style?,
    val duration: Duration,
    val model: MusicModel,
    val instrumental: Boolean,
    val coverSeed: CoverSeed,
    val status: TrackStatus,
    val createdAt: Instant,
    val isLiked: Boolean,
    val genres: List<String> = emptyList(),
    val audioUri: String? = null,
    val audioHeaders: Map<String, String> = emptyMap(),
    val customLyrics: String? = null,
    val words: List<LyricWord> = emptyList(),
    val generationId: String? = null,
    val variantIndex: Int = 0,
    val variants: Int = 1,
    val remixTrackId: String? = null,
    val referenceUploadId: String? = null,
    val quality: Quality = Quality.Auto,
)
