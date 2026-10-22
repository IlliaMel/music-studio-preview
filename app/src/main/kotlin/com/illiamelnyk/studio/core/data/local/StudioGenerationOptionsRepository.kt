package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationOptions
import com.illiamelnyk.studio.core.domain.track.GenerationOptionsRepository
import com.illiamelnyk.studio.core.domain.track.Quality

/** Shares the account boundary and the same validated capability contract as generation preflight. */
internal class StudioGenerationOptionsRepository(private val runtime: StudioAccountRuntime) :
    GenerationOptionsRepository {
    override suspend fun load(): Outcome<GenerationOptions> = runtime.operation {
        val account = runtime.requireAccount()
        val token = account.token
        if (token == null) {
            GenerationOptions(
                qualityModes = Quality.entries.toSet(),
                variantCounts = setOf(1, 2),
                customLyrics = true,
                audioReferences = true,
                catalogRemix = true,
                maxRemixSources = 4,
            )
        } else {
            val supported = runtime.api.loadGenerationCapabilities(token)
            GenerationOptions(
                qualityModes = Quality.entries.filter { it.name.lowercase() in supported.qualityModes }.toSet(),
                variantCounts = supported.variants,
                customLyrics = supported.customLyrics,
                audioReferences = supported.audioReferences,
                catalogRemix = supported.liveCatalogRemix,
                maxRemixSources = supported.maxRemixSources,
            )
        }
    }
}
