package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationOptions
import com.illiamelnyk.studio.core.domain.track.GenerationOptionsRepository
import com.illiamelnyk.studio.core.domain.track.Quality

internal class TestGenerationOptionsRepository(
    var result: Outcome<GenerationOptions> = Outcome.Success(
        GenerationOptions(Quality.entries.toSet(), setOf(1, 2), true, true, true, 4),
    ),
) : GenerationOptionsRepository {
    override suspend fun load(): Outcome<GenerationOptions> = result
}
