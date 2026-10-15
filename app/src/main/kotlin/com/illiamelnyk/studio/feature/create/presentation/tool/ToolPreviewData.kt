package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.Tool

/**
 * A tool's screen: slots still empty, ready to use, submitting, and a tool that needs a recording.
 */
class ToolPreviewData : PreviewParameterProvider<ToolState> {
    override val values = Tool.entries.map(::ToolState).asSequence()
}
