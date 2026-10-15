package com.illiamelnyk.studio.core.data.local

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
import org.json.JSONArray
import org.json.JSONObject

internal fun Track.toJson(): JSONObject = JSONObject().apply {
    put("id", id.value)
    put("generationId", generationId)
    put("variantIndex", variantIndex)
    put("variants", variants)
    put("quality", quality.name.lowercase())
    put("remixTrackId", remixTrackId)
    put("referenceUploadId", referenceUploadId)
    put("title", title)
    put("artist", artist)
    put("prompt", prompt)
    put("durationMs", duration.inWholeMilliseconds)
    put("modelId", if (model == MusicModel.V2) "music_v2" else "music_v2_5")
    put("instrumental", instrumental)
    put("coverSeed", coverSeed.value)
    put("createdAt", createdAt.toString())
    put("isLiked", isLiked)
    put("genres", JSONArray(genres))
    put("audioUrl", audioUri)
    put("customLyrics", customLyrics)
    put(
        "words",
        JSONArray(
            words.map { word ->
                JSONObject().put("word", word.text).put("startMs", word.startMs).put("endMs", word.endMs)
            },
        ),
    )
    put(
        "audioHeaders",
        JSONObject(
            audioHeaders.filter { (key, value) ->
                !key.equals("Authorization", true) ||
                    value.startsWith("Bearer dev:")
            },
        ),
    )
    style?.let {
        put(
            "style",
            JSONObject().put(
                "id",
                it.id,
            ).put("name", it.name).put("tags", JSONArray(it.tags)).put("seed", it.coverSeed.value),
        )
    }
    put(
        "status",
        when (status) {
            TrackStatus.Queued -> "queued"
            is TrackStatus.Generating -> "running"
            TrackStatus.Ready -> "done"
            is TrackStatus.Failed -> "failed"
        },
    )
    if (status is TrackStatus.Failed) {
        put(
            "error",
            JSONObject().put("code", errorCode(status.error)).put("promptSuggestion", status.suggestion),
        )
    }
}

internal fun JSONObject.toTrack(artist: String = "", headers: Map<String, String> = emptyMap()): Track {
    val styleJson = optJSONObject("style")
    val uri = stringOrNull("audioUrl")
    return Track(
        id = TrackId(getString("id")),
        generationId = stringOrNull("generationId"),
        variantIndex = optInt("variantIndex"),
        variants = optInt("variants", 1),
        quality = Quality.entries.firstOrNull { it.name.equals(optString("quality"), true) } ?: Quality.Auto,
        remixTrackId = stringOrNull("remixTrackId"),
        referenceUploadId = stringOrNull("referenceUploadId"),
        title = stringOrNull("title") ?: getString("prompt").take(40),
        artist = optString("artist", artist),
        prompt = optString("prompt"),
        style = styleJson?.let {
            Style(it.getString("id"), it.getString("name"), it.array("tags").strings(), CoverSeed(it.getString("seed")))
        },
        duration = optLong("durationMs", optLong("lengthMs", 120_000)).coerceAtLeast(1).milliseconds,
        model = if (optString("modelId") == "music_v2") MusicModel.V2 else MusicModel.V2_5,
        instrumental = optBoolean("instrumental"),
        coverSeed = CoverSeed(optString("coverSeed", getString("id"))),
        status = when (optString("status")) {
            "queued" -> TrackStatus.Queued

            "running" -> TrackStatus.Generating(0.5f)

            "done" -> TrackStatus.Ready

            else -> TrackStatus.Failed(
                optJSONObject("error")?.let {
                    apiError(it.optString("code"), it.stringOrNull("promptSuggestion"))
                }
                    ?: AppError.Server,
            )
        },
        createdAt = Instant.parse(getString("createdAt")),
        isLiked = optBoolean("isLiked"),
        genres = array("genres").strings(),
        audioUri = uri,
        audioHeaders = if (headers.isNotEmpty() &&
            uri != null
        ) {
            headers
        } else {
            optJSONObject("audioHeaders")?.let { json ->
                json.keys().asSequence().filter {
                    !it.equals("Authorization", true) ||
                        json.optString(it).startsWith("Bearer dev:")
                }
                    .associateWith { json.getString(it) }
            }.orEmpty()
        },
        customLyrics = stringOrNull("customLyrics"),
        words = array("words").objects().map { word ->
            LyricWord(word.getString("word"), word.getLong("startMs"), word.getLong("endMs"))
        },
    )
}

internal fun GenerationRequest.toJson(): JSONObject = JSONObject().apply {
    put("prompt", prompt)
    duration?.let { put("lengthMs", it.inWholeMilliseconds) }
    put("modelId", if (model == MusicModel.V2) "music_v2" else "music_v2_5")
    put("quality", quality.name.lowercase())
    put("instrumental", instrumental)
    put("customLyrics", customLyrics)
    put("referenceUploadId", referenceUploadId)
    put("remixTrackId", remixTrackId)
    put("referenceAudioUri", referenceAudioUri)
}

internal fun JSONObject.toRequest(): GenerationRequest = GenerationRequest(
    prompt = getString("prompt"),
    duration = if (has("lengthMs")) getLong("lengthMs").milliseconds else null,
    model = if (optString("modelId") == "music_v2") MusicModel.V2 else MusicModel.V2_5,
    quality = Quality.entries.firstOrNull { it.name.equals(optString("quality"), true) } ?: Quality.Auto,
    instrumental = optBoolean("instrumental"),
    customLyrics = stringOrNull("customLyrics"),
    referenceUploadId = stringOrNull("referenceUploadId"),
    remixTrackId = stringOrNull("remixTrackId"),
    referenceAudioUri = stringOrNull("referenceAudioUri"),
)

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
internal fun apiError(code: String, suggestion: String? = null): AppError = when (code) {
    "INVALID_PROMPT", "PROMPT_REJECTED", "VALIDATION" -> AppError.InvalidPrompt(suggestion)
    "QUOTA_EXCEEDED", "DAILY_LIMIT" -> AppError.QuotaExceeded
    "RATE_LIMITED" -> AppError.RateLimited
    "UNAUTHORIZED", "ACCOUNT_DELETED", "ACCOUNT_DISABLED" -> AppError.Unauthorized
    "TIMEOUT", "GENERATION_TIMEOUT" -> AppError.Timeout
    "UNSUPPORTED_MODE" -> AppError.UnsupportedMode
    "INVALID_REFERENCE" -> AppError.InvalidReference
    "PROVIDER_OUTCOME_UNKNOWN" -> AppError.ProviderOutcomeUnknown
    else -> AppError.Server
}
private fun errorCode(error: AppError): String = when (error) {
    is AppError.InvalidPrompt -> "INVALID_PROMPT"
    AppError.QuotaExceeded -> "QUOTA_EXCEEDED"
    AppError.Unauthorized -> "UNAUTHORIZED"
    AppError.Timeout -> "TIMEOUT"
    AppError.UnsupportedMode -> "UNSUPPORTED_MODE"
    AppError.InvalidReference -> "INVALID_REFERENCE"
    AppError.ProviderOutcomeUnknown -> "PROVIDER_OUTCOME_UNKNOWN"
    else -> "SERVER"
}
