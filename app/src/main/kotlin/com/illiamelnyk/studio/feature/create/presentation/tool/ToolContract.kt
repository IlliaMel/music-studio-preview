package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.ui.components.PromptPart
import com.illiamelnyk.studio.feature.create.domain.Recording
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.domain.ToolTemplate
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.trackLengthOptions
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A tool's screen: what it does, its prompt with the slots to fill in, the track length and, for a
 * tool that works on the user's audio, the row that will add it.
 *
 * @param template the prompt, its slots showing their values or their labels.
 * @param recording the "Add your voice" row of a tool that needs a recording.
 * @param slotSheet the slot whose options are open.
 * @param comingSoon the coming soon sheet that is open.
 * @param canUse everything is filled in and a credit is left.
 * @param hint why the tool can't be used yet.
 */
@Immutable
data class ToolState(
    val tool: ToolUi,
    val template: ImmutableList<PromptPart>,
    val duration: DurationOptionUi,
    val recording: RecordingUi?,
    val durations: ImmutableList<DurationOptionUi> = trackLengthOptions.filter {
        it.duration <= 3.minutes
    }.toImmutableList(),
    val slotSheet: SlotSheetUi? = null,
    val comingSoon: ComingSoonFeature? = null,
    val canUse: Boolean = false,
    val hint: UiText? = UiText.Resource(R.string.tool_needs_recording),
    val isSubmitting: Boolean = false,
    val recordingOpen: Boolean = false,
    val referenceAudio: com.illiamelnyk.studio.feature.create.domain.RecordedAudio? = null,
) {
    constructor(tool: Tool) : this(
        tool = tool.toUi(),
        template = tool.template.toParts(emptyMap()),
        duration = tool.defaultDuration.toOptionUi(),
        recording = tool.recording?.toUi(),
    )
}

/** The row that adds the user's audio to a tool, and the sheet it opens until it can. */
@Immutable
data class RecordingUi(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    val feature: ComingSoonFeature,
)

fun Recording.toUi(): RecordingUi = when (this) {
    Recording.Voice -> RecordingUi(R.string.tool_add_voice, R.string.tool_add_voice_text, ComingSoonFeature.Voice)
    Recording.Sample -> RecordingUi(R.string.tool_add_sample, R.string.tool_add_sample_text, ComingSoonFeature.Sample)
}

/** The options of one slot, the one it has highlighted. */
@Immutable
data class SlotSheetUi(val slotId: String, val label: String, val options: ImmutableList<String>, val selected: String?)

/** The template as the kit's prompt shows it: each slot with its value from [values], or its label. */
fun ToolTemplate.toParts(values: Map<String, String>): ImmutableList<PromptPart> = parts.map { part ->
    when (part) {
        is ToolTemplate.Part.Text -> PromptPart.Text(part.text)
        is ToolTemplate.Part.Slot -> PromptPart.Slot(part.slot.id, part.slot.label, values[part.slot.id])
    }
}.toImmutableList()

sealed interface ToolIntent {
    data object GenerationOptionsRefresh : ToolIntent

    data object BackClicked : ToolIntent

    data class SlotClicked(val slotId: String) : ToolIntent

    data class SlotValueSelected(val value: String) : ToolIntent

    data class DurationSelected(val duration: DurationOptionUi) : ToolIntent

    data object AddRecordingClicked : ToolIntent

    data class RecordingSelected(val audio: com.illiamelnyk.studio.feature.create.domain.RecordedAudio) : ToolIntent

    data object RecordingDismissed : ToolIntent

    /** "I want this" on the coming soon sheet. */
    data object WantClicked : ToolIntent

    data object SheetDismissed : ToolIntent

    /** The hint above Use: after options failed to load, it retries. */
    data object HintClicked : ToolIntent

    data object UseToolClicked : ToolIntent
}

sealed interface ToolEffect {
    /** Leave the tool: back to Create, where the new track shows up as it generates. */
    data object Close : ToolEffect

    data class ShowToast(val toast: ToastUi) : ToolEffect
}
