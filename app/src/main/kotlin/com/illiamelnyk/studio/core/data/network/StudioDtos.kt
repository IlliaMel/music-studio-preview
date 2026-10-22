package com.illiamelnyk.studio.core.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire contracts stay in data; they never carry a Firebase bearer or Android types. */
@Serializable
internal data class ProfileDto(
    val id: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val signInProvider: String = "google.com",
    val displayName: String? = null,
    val ageConfirmed: Boolean = false,
    val termsVersion: String? = null,
    val marketingOptIn: Boolean = false,
    val onboarding: OnboardingDto? = null,
    val canGenerate: Boolean = true,
)

@Serializable
internal data class OnboardingDto(val termsVersion: String, val missing: List<String> = emptyList())

@Serializable
internal data class ProfileUpdateDto(
    val displayName: String? = null,
    val ageConfirmed: Boolean? = null,
    val acceptedTermsVersion: String? = null,
    val marketingOptIn: Boolean? = null,
)

@Serializable
internal data class CreateTrackDto(
    val prompt: String,
    val lengthMs: Long? = null,
    val instrumental: Boolean = false,
    val modelId: String? = null,
    val quality: String = "auto",
    val variants: Int = 1,
    val customLyrics: String? = null,
    val referenceUploadId: String? = null,
    val remixTrackIds: List<String>? = null,
)

@Serializable
internal data class TrackDto(
    val id: String,
    val status: String,
    val createdAt: String,
    val prompt: String = "",
    val artist: String = "",
    val coverSeed: String? = null,
    val style: StyleDto? = null,
    val isLiked: Boolean? = null,
    val audioHeaders: Map<String, String>? = null,
    val generationId: String? = null,
    val variantIndex: Int = 0,
    val variants: Int = 1,
    val quality: String = "auto",
    val remixTrackIds: List<String>? = null,
    val remixTrackId: String? = null,
    val referenceUploadId: String? = null,
    val title: String? = null,
    val lengthMs: Long? = null,
    val durationMs: Long? = null,
    val instrumental: Boolean = false,
    val modelId: String = "music_v2_5",
    val genres: List<String> = emptyList(),
    val audioUrl: String? = null,
    val customLyrics: String? = null,
    val words: List<WordDto> = emptyList(),
    val error: ApiErrorDto? = null,
)

@Serializable
internal data class WordDto(val word: String, val startMs: Long, val endMs: Long)

@Serializable
internal data class TracksPageDto(val tracks: List<TrackDto>, val nextCursor: String? = null)

@Serializable
internal data class TitleDto(val title: String)

@Serializable
internal data class PlaylistNameDto(val name: String)

@Serializable
internal data class PlaylistDto(
    val id: String,
    val name: String,
    val createdAt: String,
    val trackIds: List<String> = emptyList(),
)

@Serializable
internal data class PlaylistsDto(val playlists: List<PlaylistDto>)

@Serializable
internal data class LikesDto(val trackIds: List<String>)

@Serializable
internal data class QuotaDto(val used: Int = 0, val remaining: Int, val limit: Int, val resetsAt: String? = null)

@Serializable
internal data class UploadDto(
    val id: String,
    val contentType: String? = null,
    val sizeBytes: Int? = null,
    val durationMs: Long? = null,
    val provider: String? = null,
    val expiresAt: String? = null,
)

@Serializable
internal data class RecordingDto(
    val id: String,
    val title: String,
    val contentType: String,
    val sizeBytes: Int,
    val durationMs: Long,
    val createdAt: String,
    val updatedAt: String,
    val audioUrl: String,
)

@Serializable
internal data class RecordingsDto(val recordings: List<RecordingDto>)

@Serializable
internal data class CapabilitiesDto(
    val provider: String,
    val qualityModes: List<String>,
    val customLyrics: Boolean,
    val variants: List<Int>,
    val audioReferences: String,
    val liveCatalogRemix: Boolean,
    val maxRemixSources: Int = 1,
)

@Serializable
internal data class ApiErrorDto(
    val code: String = "INTERNAL",
    val message: String? = null,
    val promptSuggestion: String? = null,
    val details: JsonElement? = null,
)

@Serializable
internal data class ErrorEnvelopeDto(val error: ApiErrorDto)

@Serializable
internal data class StyleDto(val id: String, val name: String, val tags: List<String>, val seed: String)
