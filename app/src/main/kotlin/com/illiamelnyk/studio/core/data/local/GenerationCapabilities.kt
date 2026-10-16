package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import kotlin.math.ceil
import org.json.JSONObject

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
        val sources = request.remixSourceIds
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
            if (lines.any { it.length > 200 }) return AppError.UnsupportedMode
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
        fun parse(json: JSONObject): GenerationCapabilities {
            val provider = json.getString("provider")
            val quality = json.getJSONArray("qualityModes").strings().toSet()
            val rawVariants = json.getJSONArray("variants")
            val variants = (0 until rawVariants.length()).map { rawVariants.getInt(it) }.toSet()
            val references = json.getString("audioReferences")
            require(provider in setOf("mock", "elevenlabs"))
            require(quality.isNotEmpty() && quality.all { it in setOf("auto", "deep", "fast") })
            require(variants.isNotEmpty() && variants.all { it in 1..2 })
            require(references in setOf("mock", "enabled-enterprise", "disabled-enterprise-entitlement-required"))
            val maxRemixSources = if (json.has("maxRemixSources")) json.getInt("maxRemixSources") else 1
            require(maxRemixSources in 0..4)
            return GenerationCapabilities(
                provider,
                quality,
                json.getBoolean("customLyrics"),
                variants,
                references != "disabled-enterprise-entitlement-required",
                json.getBoolean("liveCatalogRemix"),
                maxRemixSources,
            )
        }
    }
}
