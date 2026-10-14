package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.core.domain.CoverSeed
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
)
