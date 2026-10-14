package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.ToolUi
import com.illiamelnyk.studio.feature.create.presentation.toUi

/**
 * A tool's screen: what it does and a button that generates a track with it.
 *
 * @param needsRecording the tool works on the user's audio, which can't be recorded yet, so it
 *   can't be used.
 */
@Immutable
data class ToolState(
    val tool: ToolUi,
    @param:StringRes val description: Int,
    val needsRecording: Boolean,
    val isSubmitting: Boolean = false,
    val notice: UiText? = null,
) {
    constructor(tool: Tool) : this(tool.toUi(), tool.descriptionRes, tool.needsRecording)
}

sealed interface ToolIntent {
    data object BackClicked : ToolIntent

    data object UseToolClicked : ToolIntent
}

sealed interface ToolEffect {
    /** Leave the tool: back to Create, where a new track shows up as it generates. */
    data object Close : ToolEffect
}

@get:StringRes
private val Tool.descriptionRes: Int
    get() = when (this) {
        Tool.VoiceToSong -> R.string.tool_voice_to_song_description
        Tool.Genreshift -> R.string.tool_genreshift_description
        Tool.SampleToSong -> R.string.tool_sample_to_song_description
        Tool.Unplugged -> R.string.tool_unplugged_description
    }
