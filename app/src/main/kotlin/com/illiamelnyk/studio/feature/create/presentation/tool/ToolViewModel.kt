package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ToolViewModel(private val tool: Tool, private val generateTracks: GenerateTracks, credits: CreditsRepository) :
    MviViewModel<ToolState, ToolIntent, ToolEffect>(ToolState(tool)) {

    /** What the user picked for each slot, by slot id. */
    private var values: Map<String, String> = emptyMap()

    /** `null` until the credits are known. */
    private var creditsLeft: Int? = null

    init {
        credits.credits.onEach { credits ->
            creditsLeft = credits.remaining
            refresh()
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ToolIntent) = when (intent) {
        ToolIntent.BackClicked -> sendEffect(ToolEffect.Close)
        is ToolIntent.SlotClicked -> openSlot(intent.slotId)
        is ToolIntent.SlotValueSelected -> fillSlot(intent.value)
        is ToolIntent.DurationSelected -> setState { copy(duration = intent.duration) }
        ToolIntent.AddRecordingClicked -> setState { copy(comingSoon = recording?.feature) }
        ToolIntent.WantClicked -> want()
        ToolIntent.SheetDismissed -> setState { copy(slotSheet = null, comingSoon = null) }
        ToolIntent.UseToolClicked -> useTool()
    }

    private fun openSlot(id: String) {
        val slot = tool.template.slots.find { it.id == id } ?: return
        setState { copy(slotSheet = SlotSheetUi(slot.id, slot.label, slot.options.toImmutableList(), values[slot.id])) }
    }

    private fun fillSlot(value: String) {
        val slotId = state.value.slotSheet?.slotId ?: return
        values = values + (slotId to value)
        setState { copy(slotSheet = null) }
        refresh()
    }

    private fun want() {
        val feature = state.value.comingSoon ?: return
        setState { copy(comingSoon = null) }
        val toast = ToastUi(
            title = UiText.Resource(R.string.create_toast_noted),
            message = UiText.Resource(R.string.create_toast_noted_text, UiText.Resource(feature.title)),
        )
        sendEffect(ToolEffect.ShowToast(toast))
    }

    private fun useTool() {
        if (!state.value.canUse) return
        val prompt = tool.template.fill(values) ?: return
        val settings = GenerationSettings(duration = state.value.duration.duration)
        setState { copy(isSubmitting = true) }
        refresh()
        viewModelScope.launch {
            when (val result = generateTracks(prompt, settings)) {
                is Outcome.Success -> sendEffect(ToolEffect.Close)

                is Outcome.Failure -> {
                    setState { copy(isSubmitting = false) }
                    refresh()
                    val toast = ToastUi(
                        title = UiText.Resource(R.string.create_toast_error),
                        message = result.error.toUiText(),
                        kind = StudioToastKind.Error,
                    )
                    sendEffect(ToolEffect.ShowToast(toast))
                }
            }
        }
    }

    /** Shows the slots' values and works out whether the tool can be used. */
    private fun refresh() {
        val tool = this.tool // in the state's scope, `tool` is the state's ToolUi
        setState { copy(template = tool.template.toParts(values)).checked(tool, values, creditsLeft) }
    }
}
