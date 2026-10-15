package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlinx.coroutines.flow.first

/** Starts a batch of one or two variations with one quota credit. Auto leaves the duration unset. */
class GenerateTracks(private val tracks: TrackRepository, private val credits: CreditsRepository) {

    suspend operator fun invoke(prompt: String, settings: GenerationSettings): Outcome<List<Track>> {
        val text = prompt.trim()
        if (!Prompt.isValid(text)) return Outcome.Failure(AppError.InvalidPrompt(suggestion = null))
        if (credits.credits.first().remaining < 1) return Outcome.Failure(AppError.QuotaExceeded)
        val request = GenerationRequest(
            prompt = text,
            duration = settings.duration,
            model = settings.model,
            quality = settings.quality,
            instrumental = settings.lyricsMode == LyricsMode.Instrumental,
        )
        return tracks.create(request, settings.variants)
    }
}
