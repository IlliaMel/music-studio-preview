package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ToolViewModel(
    private val tool: Tool,
    private val generate: GenerateTracks? = null,
    credits: CreditsRepository? = null,
) : MviViewModel<ToolState, ToolIntent, ToolEffect>(ToolState(tool)) {

    private var values: Map<String, String> = emptyMap()
    private var available = 0

    init {
        credits?.credits?.onEach {
            available = it.remaining
            refresh()
        }?.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ToolIntent) {
        when (intent) {
            ToolIntent.BackClicked -> sendEffect(ToolEffect.Close)

            is ToolIntent.SlotClicked -> openSlot(intent.slotId)

            is ToolIntent.SlotValueSelected -> fillSlot(intent.value)

            is ToolIntent.DurationSelected -> setState {
                if (intent.duration in durations) copy(duration = intent.duration) else this
            }

            ToolIntent.AddRecordingClicked -> setState { copy(recordingOpen = true) }

            is ToolIntent.RecordingSelected -> {
                setState { copy(referenceAudio = intent.audio, recordingOpen = false) }
                refresh()
            }

            ToolIntent.RecordingDismissed -> setState { copy(recordingOpen = false) }

            ToolIntent.WantClicked -> want()

            ToolIntent.SheetDismissed -> setState { copy(slotSheet = null, comingSoon = null) }

            ToolIntent.UseToolClicked -> submit()
        }
    }

    private fun openSlot(id: String) {
        val slot = tool.template.slots.find { it.id == id } ?: return
        setState { copy(slotSheet = SlotSheetUi(slot.id, slot.label, slot.options.toImmutableList(), values[slot.id])) }
    }

    private fun fillSlot(value: String) {
        val slotId = state.value.slotSheet?.slotId ?: return
        if (tool.template.slots.first { it.id == slotId }.options.none { it == value }) return
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

    private fun submit() {
        val current = state.value
        if (!current.canUse || current.isSubmitting) return
        val prompt = tool.template.fill(values) ?: return
        val generator = generate ?: return
        setState { copy(isSubmitting = true, canUse = false) }
        viewModelScope.launch {
            val settings = GenerationSettings(
                duration = current.duration.duration,
                referenceAudioUri = current.referenceAudio?.uri,
            )
            val result = generator(prompt, settings)
            setState { copy(isSubmitting = false) }
            when (result) {
                is Outcome.Success -> sendEffect(ToolEffect.Close)

                is Outcome.Failure -> {
                    val error = ToastUi(UiText.Resource(R.string.create_toast_error), result.error.toUiText())
                    sendEffect(ToolEffect.ShowToast(error))
                }
            }
            refresh()
        }
    }

    private fun refresh() {
        val template = tool.template.toParts(values)
        val filled = tool.template.fill(values) != null
        setState {
            copy(
                template = template,
                canUse = filled &&
                    referenceAudio != null && available > 0 && !isSubmitting,
                hint = when {
                    referenceAudio == null -> UiText.Resource(R.string.tool_needs_recording)
                    !filled -> UiText.Resource(R.string.tool_fill_options)
                    available <= 0 -> UiText.Resource(R.string.error_quota_exceeded)
                    else -> null
                },
            )
        }
    }
}
