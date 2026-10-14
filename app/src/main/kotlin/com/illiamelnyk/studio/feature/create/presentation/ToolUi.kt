package com.illiamelnyk.studio.feature.create.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.feature.create.domain.Tool

/**
 * A tool as its tile and its screen show it.
 *
 * @param needsRecording the tool works on the user's own audio, which can't be added yet: its tile
 *   is muted with a SOON badge, and it still opens to explain itself.
 */
@Immutable
data class ToolUi(
    val tool: Tool,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val coverSeed: String,
    val needsRecording: Boolean,
)

fun Tool.toUi(): ToolUi = ToolUi(
    tool = this,
    title = when (this) {
        Tool.VoiceToSong -> R.string.tool_voice_to_song
        Tool.Genreshift -> R.string.tool_genreshift
        Tool.SampleToSong -> R.string.tool_sample_to_song
        Tool.Unplugged -> R.string.tool_unplugged
    },
    description = when (this) {
        Tool.VoiceToSong -> R.string.tool_voice_to_song_description
        Tool.Genreshift -> R.string.tool_genreshift_description
        Tool.SampleToSong -> R.string.tool_sample_to_song_description
        Tool.Unplugged -> R.string.tool_unplugged_description
    },
    coverSeed = name,
    needsRecording = recording != null,
)
