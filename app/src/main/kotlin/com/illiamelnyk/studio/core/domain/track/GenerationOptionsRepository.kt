package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.core.domain.Outcome

/** Features supported by the connected generator, independently of the user's credit balance. */
data class GenerationOptions(
    val qualityModes: Set<Quality>,
    val variantCounts: Set<Int>,
    val customLyrics: Boolean,
    val audioReferences: Boolean,
    val catalogRemix: Boolean,
    val maxRemixSources: Int,
)

/** Loads current capabilities; an unavailable response must not be treated as permission. */
interface GenerationOptionsRepository {
    suspend fun load(): Outcome<GenerationOptions>
}
