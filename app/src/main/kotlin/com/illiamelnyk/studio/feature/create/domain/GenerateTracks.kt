package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlinx.coroutines.flow.first

/**
 * Starts the tracks of one prompt from the composer: [GenerationSettings.variants] generations of
 * the same request, one after another.
 *
 * Nothing starts when the credits left don't cover every variant, so a 2× never stops half-way for
 * lack of credits. The server still has the last word: a generation it refuses ends the run with
 * its error, and the tracks started before it carry on.
 */
class GenerateTracks(private val tracks: TrackRepository, private val credits: CreditsRepository) {

    suspend operator fun invoke(prompt: String, settings: GenerationSettings): Outcome<List<Track>> {
        val text = prompt.trim()
        if (!Prompt.isValid(text)) return Outcome.Failure(AppError.InvalidPrompt(suggestion = null))
        if (credits.credits.first().remaining < settings.variants) return Outcome.Failure(AppError.QuotaExceeded)
        val request = GenerationRequest(
            prompt = text,
            duration = settings.duration,
            model = settings.model,
            quality = settings.quality,
            instrumental = settings.instrumental,
            style = settings.style,
        )
        val started = mutableListOf<Track>()
        repeat(settings.variants) {
            when (val result = tracks.create(request)) {
                is Outcome.Success -> started += result.value
                is Outcome.Failure -> return result
            }
        }
        return Outcome.Success(started)
    }
}
