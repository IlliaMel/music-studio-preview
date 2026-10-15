package com.illiamelnyk.studio.feature.create.presentation.tool

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import kotlinx.collections.immutable.toImmutableList

class ToolViewModel(private val tool: Tool) : MviViewModel<ToolState, ToolIntent, ToolEffect>(ToolState(tool)) {

    private var values: Map<String, String> = emptyMap()

    override fun onIntent(intent: ToolIntent) = when (intent) {
        ToolIntent.BackClicked -> sendEffect(ToolEffect.Close)

        is ToolIntent.SlotClicked -> openSlot(intent.slotId)

        is ToolIntent.SlotValueSelected -> fillSlot(intent.value)

        is ToolIntent.DurationSelected -> setState {
            if (intent.duration in durations) copy(duration = intent.duration) else this
        }

        ToolIntent.AddRecordingClicked -> setState { copy(comingSoon = recording?.feature) }

        ToolIntent.WantClicked -> want()

        ToolIntent.SheetDismissed -> setState { copy(slotSheet = null, comingSoon = null) }

        ToolIntent.UseToolClicked -> Unit
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

    private fun refresh() {
        val template = tool.template.toParts(values)
        setState { copy(template = template) }
    }
}
