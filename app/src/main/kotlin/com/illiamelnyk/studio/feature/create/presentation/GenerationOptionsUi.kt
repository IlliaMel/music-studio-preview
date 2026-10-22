package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.core.domain.track.GenerationOptions
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import kotlinx.collections.immutable.toImmutableList

internal fun settingsForOptions(quality: Quality, options: GenerationOptions?): Quality = when {
    options == null || quality in options.qualityModes -> quality
    Quality.Auto in options.qualityModes -> Quality.Auto
    else -> Quality.entries.first { it in options.qualityModes }
}

internal fun variantsForOptions(variants: Int, options: GenerationOptions?): Int =
    if (options == null || variants in options.variantCounts) variants else options.variantCounts.min()

internal fun GenerationSettings.normalized(options: GenerationOptions?): GenerationSettings = copy(
    quality = settingsForOptions(quality, options),
    variants = variantsForOptions(variants, options),
)

internal fun SettingsUi.withOptions(options: GenerationOptions?): SettingsUi = copy(
    qualityOptions = Quality.entries.filter { it in options?.qualityModes.orEmpty() }.toImmutableList(),
    variantOptions = options?.variantCounts.orEmpty().sorted().toImmutableList(),
    customLyricsSupported = options?.customLyrics == true,
    optionsReady = options != null,
)
