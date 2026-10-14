package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.Tool

/** A tool's screen: ready to use, submitting, failed, and a tool that needs a recording. */
class ToolPreviewData : PreviewParameterProvider<ToolState> {
    override val values = sequenceOf(
        ToolState(Tool.Genreshift),
        ToolState(Tool.Genreshift).copy(isSubmitting = true),
        ToolState(Tool.Unplugged).copy(notice = UiText.Resource(R.string.error_quota_exceeded)),
        ToolState(Tool.VoiceToSong),
    )
}
