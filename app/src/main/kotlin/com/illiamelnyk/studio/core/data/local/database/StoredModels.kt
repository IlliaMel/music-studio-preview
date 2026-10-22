package com.illiamelnyk.studio.core.data.local.database

import androidx.room.TypeConverter
import com.illiamelnyk.studio.core.data.local.DeletionAudio
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.Style
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class StoredStyle(val id: String, val name: String, val tags: List<String>, val cover: String)

@Serializable internal data class StoredWord(val text: String, val start: Long, val end: Long)

/** Typed collection converters only. Account/track scalar fields are real SQL columns. */
internal class StudioTypeConverters {
    @TypeConverter fun strings(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter fun strings(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter fun words(value: List<StoredWord>): String = Json.encodeToString(value)

    @TypeConverter fun words(value: String): List<StoredWord> = Json.decodeFromString(value)

    @TypeConverter fun style(value: StoredStyle?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter fun style(value: String?): StoredStyle? = value?.let { Json.decodeFromString(it) }

    @TypeConverter fun audio(value: List<DeletionAudio>): String = Json.encodeToString(value)

    @TypeConverter fun audio(value: String): List<DeletionAudio> = Json.decodeFromString(value)
}

/** Embedded by both owned tracks and catalog playlist members; no transport or credentials. */
internal data class StoredTrackFields(
    val title: String,
    val artist: String,
    val prompt: String,
    val style: StoredStyle?,
    val durationMs: Long,
    val model: String,
    val instrumental: Boolean,
    val cover: String,
    val status: String,
    val progress: Float,
    val error: String?,
    val suggestion: String?,
    val createdAt: String,
    val isLiked: Boolean,
    val genres: List<String>,
    val audioUri: String?,
    val customLyrics: String?,
    val words: List<StoredWord>,
    val generationId: String?,
    val variantIndex: Int,
    val variants: Int,
    val referenceUploadId: String?,
    val quality: String,
    val remixTrackIds: List<String>,
) {
    fun track(id: String): Track = Track(
        id = TrackId(id), title = title, artist = artist, prompt = prompt,
        style = style?.let { Style(it.id, it.name, it.tags, CoverSeed(it.cover)) },
        duration = durationMs.milliseconds, model = MusicModel.valueOf(model), instrumental = instrumental,
        coverSeed = CoverSeed(cover),
        status = when (status) {
            "queued" -> TrackStatus.Queued
            "running" -> TrackStatus.Generating(progress)
            "done" -> TrackStatus.Ready
            else -> TrackStatus.Failed(storedError(error, suggestion))
        },
        createdAt = Instant.parse(createdAt), isLiked = isLiked, genres = genres, audioUri = audioUri,
        customLyrics = customLyrics, words = words.map { LyricWord(it.text, it.start, it.end) },
        generationId = generationId, variantIndex = variantIndex, variants = variants,
        referenceUploadId = referenceUploadId, quality = Quality.valueOf(quality), remixTrackIds = remixTrackIds,
    )
}

internal fun Track.storedFields() = StoredTrackFields(
    title = title, artist = artist, prompt = prompt,
    style = style?.let { StoredStyle(it.id, it.name, it.tags, it.coverSeed.value) },
    durationMs = duration.inWholeMilliseconds, model = model.name, instrumental = instrumental,
    cover = coverSeed.value,
    status = when (status) {
        TrackStatus.Queued -> "queued"
        is TrackStatus.Generating -> "running"
        TrackStatus.Ready -> "done"
        is TrackStatus.Failed -> "failed"
    },
    progress = (status as? TrackStatus.Generating)?.progress ?: 0f,
    error = (status as? TrackStatus.Failed)?.error?.storedCode(),
    suggestion = ((status as? TrackStatus.Failed)?.error as? AppError.InvalidPrompt)?.suggestion,
    createdAt = createdAt.toString(), isLiked = isLiked, genres = genres, audioUri = audioUri,
    customLyrics = customLyrics, words = words.map { StoredWord(it.text, it.startMs, it.endMs) },
    generationId = generationId, variantIndex = variantIndex, variants = variants,
    referenceUploadId = referenceUploadId, quality = quality.name, remixTrackIds = remixTrackIds,
)

internal data class StoredRequestFields(
    val prompt: String,
    val durationMs: Long?,
    val model: String,
    val quality: String,
    val instrumental: Boolean,
    val style: StoredStyle?,
    val customLyrics: String?,
    val referenceUploadId: String?,
    val referenceAudioUri: String?,
    val remixTrackIds: List<String>,
) {
    fun request() = GenerationRequest(
        prompt, durationMs?.milliseconds, MusicModel.valueOf(model),
        Quality.valueOf(quality), instrumental, style?.let { Style(it.id, it.name, it.tags, CoverSeed(it.cover)) },
        customLyrics, referenceUploadId, referenceAudioUri, remixTrackIds,
    )
}

internal fun GenerationRequest.storedFields() = StoredRequestFields(
    prompt, duration?.inWholeMilliseconds,
    model.name, quality.name, instrumental, style?.let { StoredStyle(it.id, it.name, it.tags, it.coverSeed.value) },
    customLyrics, referenceUploadId, referenceAudioUri, remixTrackIds,
)

private fun AppError.storedCode(): String = when (this) {
    is AppError.InvalidPrompt -> "prompt"
    AppError.QuotaExceeded -> "quota"
    AppError.ProviderUnavailable -> "provider_unavailable"
    AppError.RateLimited -> "rate"
    AppError.GenerationNotAllowed -> "generation_not_allowed"
    AppError.ServiceDailyLimit -> "service_daily_limit"
    AppError.Unauthorized -> "auth"
    AppError.Network -> "network"
    AppError.Timeout -> "timeout"
    AppError.Server -> "server"
    AppError.UnsupportedMode -> "unsupported"
    AppError.InvalidReference -> "reference"
    AppError.ProviderOutcomeUnknown -> "provider_unknown"
    is AppError.LocalCleanupFailed -> if (remoteDeletionUnconfirmed) "cleanup_unconfirmed" else "cleanup"
    AppError.Unknown -> "unknown"
}

private fun storedError(code: String?, suggestion: String?): AppError = when (code) {
    "prompt" -> AppError.InvalidPrompt(suggestion)
    "quota" -> AppError.QuotaExceeded
    "provider_unavailable" -> AppError.ProviderUnavailable
    "rate" -> AppError.RateLimited
    "generation_not_allowed" -> AppError.GenerationNotAllowed
    "service_daily_limit" -> AppError.ServiceDailyLimit
    "auth" -> AppError.Unauthorized
    "network" -> AppError.Network
    "timeout" -> AppError.Timeout
    "server" -> AppError.Server
    "unsupported" -> AppError.UnsupportedMode
    "reference" -> AppError.InvalidReference
    "provider_unknown" -> AppError.ProviderOutcomeUnknown
    "cleanup" -> AppError.LocalCleanupFailed(false)
    "cleanup_unconfirmed" -> AppError.LocalCleanupFailed(true)
    else -> AppError.Unknown
}
