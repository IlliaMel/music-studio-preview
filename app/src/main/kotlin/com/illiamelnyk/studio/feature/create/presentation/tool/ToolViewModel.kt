package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.feature.create.domain.Tool
import kotlinx.coroutines.launch

class ToolViewModel(private val tool: Tool, private val tracks: TrackRepository) :
    MviViewModel<ToolState, ToolIntent, ToolEffect>(ToolState(tool)) {

    override fun onIntent(intent: ToolIntent) = when (intent) {
        ToolIntent.BackClicked -> sendEffect(ToolEffect.Close)
        ToolIntent.UseToolClicked -> useTool()
    }

    private fun useTool() {
        val state = state.value
        if (state.needsRecording || state.isSubmitting) return
        setState { copy(isSubmitting = true, notice = null) }
        viewModelScope.launch {
            when (val result = tracks.create(GenerationRequest(tool.promptTemplate, tool.defaultDuration))) {
                is Outcome.Success -> sendEffect(ToolEffect.Close)
                is Outcome.Failure -> setState { copy(isSubmitting = false, notice = result.error.toUiText()) }
            }
        }
    }
}
