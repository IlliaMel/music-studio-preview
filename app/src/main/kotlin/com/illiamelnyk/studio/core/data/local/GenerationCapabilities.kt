package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.network.CapabilitiesDto
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import kotlin.math.ceil

internal data class GenerationCapabilities(
    val provider: String,
    val qualityModes: Set<String>,
    val customLyrics: Boolean,
    val variants: Set<Int>,
    val audioReferences: Boolean,
    val liveCatalogRemix: Boolean,
    val maxRemixSources: Int,
) {
    fun rejection(request: GenerationRequest, count: Int): AppError? {
        if (request.quality.name.lowercase() !in qualityModes || count !in variants) return AppError.UnsupportedMode
        val hasReference = request.referenceAudioUri != null || request.referenceUploadId != null
        val sources = request.remixTrackIds
        val hasRemix = sources.isNotEmpty()
        if (sources.size > maxRemixSources) return AppError.UnsupportedMode
        if (sources.distinct().size != sources.size || sources.any { it.isBlank() }) return AppError.InvalidReference
        if ((hasReference || hasRemix) && !audioReferences) return AppError.UnsupportedMode
        if (provider == "elevenlabs" && hasRemix && !liveCatalogRemix &&
            sources.any { !UUID_PATTERN.matches(it) }
        ) {
            return AppError.UnsupportedMode
        }
        if (hasReference && hasRemix) return AppError.InvalidReference
        if (request.customLyrics != null && (!customLyrics || request.instrumental)) return AppError.UnsupportedMode
        if (provider == "elevenlabs") {
            val lines = request.customLyrics?.split('\n').orEmpty()
            if (lines.any { it.codePointCount(0, it.length) > 200 }) return AppError.UnsupportedMode
            if (Regex("(?m)^[ \\t]*\\[([^\\]\\r\\n]*)\\]")
                    .findAll(request.customLyrics.orEmpty()).any { match ->
                        val name = match.groupValues[1]
                        name.codePointCount(0, name.length) !in 1..100
                    }
            ) {
                return AppError.UnsupportedMode
            }
            if (request.customLyrics != null || hasReference || hasRemix) {
                val length = request.duration?.inWholeMilliseconds ?: 120_000L
                val chunks = maxOf(ceil(length / 120_000.0).toInt(), ceil(lines.size / 30.0).toInt(), 1)
                if (chunks > 30 || length / chunks < 3_000) return AppError.UnsupportedMode
            }
        }
        return null
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        fun from(dto: CapabilitiesDto): GenerationCapabilities {
            val quality = dto.qualityModes.toSet()
            val variants = dto.variants.toSet()
            require(dto.provider in setOf("mock", "elevenlabs"))
            require(quality.isNotEmpty() && quality.all { it in setOf("auto", "deep", "fast") })
            require(variants.isNotEmpty() && variants.all { it in 1..2 })
            require(
                dto.audioReferences in setOf("mock", "enabled-enterprise", "disabled-enterprise-entitlement-required"),
            )
            require(dto.maxRemixSources in 0..4)
            return GenerationCapabilities(
                dto.provider,
                quality,
                dto.customLyrics,
                variants,
                dto.audioReferences != "disabled-enterprise-entitlement-required",
                dto.liveCatalogRemix,
                dto.maxRemixSources,
            )
        }
    }
}
