package com.illiamelnyk.studio.feature.create.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.core.presentation.track.toUi
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CreateViewModel(private val tracks: TrackRepository, private val playback: PlaybackController) :
    MviViewModel<CreateState, CreateIntent, CreateEffect>(CreateState()) {

    /** The tracks as the repository last sent them, to build the queue when one is tapped. */
    private var myTracks: List<Track> = emptyList()

    init {
        combine(
            tracks.observeMine(),
            playback.state.map { it.current?.id }.distinctUntilChanged(),
        ) { list, playingId -> list to playingId }
            .onEach { (list, playingId) ->
                myTracks = list
                setState {
                    copy(
                        tracks = list.map(Track::toUi).toImmutableList(),
                        playingTrackId = playingId,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: CreateIntent) = when (intent) {
        is CreateIntent.ToolClicked -> sendEffect(CreateEffect.OpenTool(intent.tool))
        is CreateIntent.TrackClicked -> play(intent.id)
        is CreateIntent.RetryClicked -> retry(intent.id)
        is CreateIntent.GenerateDemoClicked -> generate(if (intent.nameAnArtist) REJECTED_PROMPT else DEMO_PROMPT)
    }

    /** Plays the ready tracks from the one tapped; tracks still composing or failed can't play. */
    private fun play(id: TrackId) {
        val ready = myTracks.filter { it.status == TrackStatus.Ready }
        val index = ready.indexOfFirst { it.id == id }
        if (index >= 0) playback.play(ready, index)
    }

    private fun retry(id: TrackId) {
        viewModelScope.launch {
            val result = tracks.retry(id)
            setState { copy(notice = (result as? Outcome.Failure)?.error?.toUiText()) }
        }
    }

    private fun generate(prompt: String) {
        if (state.value.isSubmitting) return
        setState { copy(isSubmitting = true, notice = null) }
        viewModelScope.launch {
            val result = tracks.create(GenerationRequest(prompt, DEMO_DURATION))
            setState { copy(isSubmitting = false, notice = (result as? Outcome.Failure)?.error?.toUiText()) }
        }
    }

    private companion object {
        // Until the composer exists, the demo buttons send fixed prompts.
        const val DEMO_PROMPT = "Dreamy synth-pop about a night train, warm pads and a catchy chorus"
        const val REJECTED_PROMPT = "Dreamy synth-pop in the style of a famous band, with a catchy chorus"
        val DEMO_DURATION = 2.minutes
    }
}
