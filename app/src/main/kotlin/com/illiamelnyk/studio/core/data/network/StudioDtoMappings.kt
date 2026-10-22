package com.illiamelnyk.studio.core.data.network

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.Style
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal fun ProfileDto.toSession(dataMode: SessionDataMode = SessionDataMode.DevelopmentServer) = Session(
    User(
        UserId(id),
        email,
        emailVerified,
        if (signInProvider == "password" ||
            signInProvider.equals("Email", true)
        ) {
            SignInMethod.Email
        } else {
            SignInMethod.Google
        },
        displayName,
        ageConfirmed,
        termsVersion,
        marketingOptIn,
        canGenerate,
    ),
    Onboarding(emptySet(), onboarding?.termsVersion ?: "2026-10-14"),
    dataMode = dataMode,
)

internal fun ProfileUpdate.toDto() = ProfileUpdateDto(
    displayName = displayName?.value,
    ageConfirmed = true.takeIf { confirmAge },
    acceptedTermsVersion = acceptedTermsVersion,
    marketingOptIn = marketingOptIn,
)

internal fun GenerationRequest.toCreateTrackDto(variants: Int, uploadedReference: String? = referenceUploadId) =
    CreateTrackDto(
        prompt = prompt, lengthMs = duration?.inWholeMilliseconds,
        modelId = if (model == MusicModel.V2) "music_v2" else "music_v2_5",
        instrumental = instrumental, quality = quality.name.lowercase(), variants = variants,
        customLyrics = customLyrics, referenceUploadId = uploadedReference,
        remixTrackIds = remixTrackIds.takeIf { it.isNotEmpty() },
    )

internal fun TrackDto.toTrack(artist: String = ""): Track = Track(
    id = TrackId(id), title = title ?: prompt.take(40), artist = this.artist.ifBlank { artist },
    prompt = prompt, style = style?.let { Style(it.id, it.name, it.tags, CoverSeed(it.seed)) },
    duration = (durationMs ?: lengthMs ?: 120_000).coerceAtLeast(1).milliseconds,
    model = if (modelId == "music_v2") MusicModel.V2 else MusicModel.V2_5,
    instrumental = instrumental, coverSeed = CoverSeed(coverSeed ?: id),
    status = when (status) {
        "queued" -> TrackStatus.Queued
        "running" -> TrackStatus.Generating(0.5f)
        "done" -> TrackStatus.Ready
        else -> TrackStatus.Failed(error?.let { serverAppError(it.code, it.promptSuggestion) } ?: AppError.Server)
    },
    createdAt = Instant.parse(createdAt), isLiked = false, genres = genres,
    audioUri = audioUrl, customLyrics = customLyrics,
    words = words.map { LyricWord(it.word, it.startMs, it.endMs) },
    generationId = generationId, variantIndex = variantIndex, variants = variants,
    remixTrackIds = remixTrackIds ?: listOfNotNull(remixTrackId),
    referenceUploadId = referenceUploadId,
    quality = Quality.entries.firstOrNull { it.name.equals(quality, true) } ?: Quality.Auto,
)
