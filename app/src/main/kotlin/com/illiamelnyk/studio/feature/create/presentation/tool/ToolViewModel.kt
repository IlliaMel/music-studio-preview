package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.track.GenerationOptions
import com.illiamelnyk.studio.core.domain.track.GenerationOptionsRepository
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
    private val generate: GenerateTracks,
    credits: CreditsRepository,
    private val generationOptions: GenerationOptionsRepository,
) : MviViewModel<ToolState, ToolIntent, ToolEffect>(ToolState(tool)) {

    private var values: Map<String, String> = emptyMap()
    private var available = 0
    private var options: GenerationOptions? = null
    private var optionsLoading = false
    private var optionsFailed = false

    init {
        loadOptions()
        credits.credits.onEach {
            available = it.remaining
            refresh()
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ToolIntent) {
        when (intent) {
            ToolIntent.GenerationOptionsRefresh -> loadOptions()

            ToolIntent.BackClicked -> sendEffect(ToolEffect.Close)

            is ToolIntent.SlotClicked -> openSlot(intent.slotId)

            is ToolIntent.SlotValueSelected -> fillSlot(intent.value)

            is ToolIntent.DurationSelected -> setState {
                if (intent.duration in durations) copy(duration = intent.duration) else this
            }

            ToolIntent.AddRecordingClicked -> if (options?.audioReferences == true) {
                setState { copy(recordingOpen = true) }
            } else if (optionsFailed) {
                loadOptions()
            }

            is ToolIntent.RecordingSelected -> {
                // Only a known "no" turns the recording away: while the options reload or failed to,
                // it is kept, and Use stays disabled until they say references work.
                if (options?.audioReferences == false) return
                setState { copy(referenceAudio = intent.audio, recordingOpen = false) }
                if (optionsFailed) loadOptions() else refresh()
            }

            ToolIntent.HintClicked -> if (optionsFailed) loadOptions()

            ToolIntent.RecordingDismissed -> setState { copy(recordingOpen = false) }

            ToolIntent.WantClicked -> want()

            ToolIntent.SheetDismissed -> setState { copy(slotSheet = null, comingSoon = null) }

            ToolIntent.UseToolClicked -> submit()
        }
    }

    private fun loadOptions() {
        if (optionsLoading) return
        optionsLoading = true
        optionsFailed = false
        options = null
        refresh()
        viewModelScope.launch {
            try {
                when (val result = generationOptions.load()) {
                    is Outcome.Success -> {
                        options = result.value
                        if (!result.value.audioReferences) setState { copy(recordingOpen = false) }
                    }

                    is Outcome.Failure -> optionsFailed = true
                }
            } finally {
                optionsLoading = false
                refresh()
            }
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
        setState { copy(isSubmitting = true, canUse = false) }
        viewModelScope.launch {
            val settings = GenerationSettings(
                duration = current.duration.duration,
                referenceAudioUri = current.referenceAudio?.uri,
            )
            val result = generate(prompt, settings)
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
                recording = this@ToolViewModel.tool.recording?.toUi().takeIf { options?.audioReferences != false },
                canUse = options?.audioReferences == true && filled &&
                    referenceAudio != null && available > 0 && !isSubmitting,
                hint = when {
                    optionsFailed -> UiText.Resource(R.string.create_options_failed)
                    options == null -> UiText.Resource(R.string.create_options_loading)
                    options?.audioReferences != true -> UiText.Resource(R.string.create_options_reference_unsupported)
                    referenceAudio == null -> UiText.Resource(R.string.tool_needs_recording)
                    !filled -> UiText.Resource(R.string.tool_fill_options)
                    available <= 0 -> UiText.Resource(R.string.error_quota_exceeded)
                    else -> null
                },
            )
        }
    }
}
