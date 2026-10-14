package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.Tool

/**
 * A tool's screen: slots still empty, ready to use, submitting, and a tool that needs a recording.
 */
class ToolPreviewData : PreviewParameterProvider<ToolState> {
    private val filled = mapOf("from" to "Piano", "to" to "Synthwave")

    override val values = sequenceOf(
        ToolState(Tool.Genreshift).copy(hint = UiText.Resource(R.string.tool_fill_slots)),
        ToolState(Tool.Genreshift).copy(template = Tool.Genreshift.template.toParts(filled), canUse = true),
        ToolState(Tool.Genreshift).copy(template = Tool.Genreshift.template.toParts(filled), isSubmitting = true),
        ToolState(Tool.VoiceToSong).copy(hint = UiText.Resource(R.string.tool_needs_recording)),
    )
}
