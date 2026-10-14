package com.illiamelnyk.studio.feature.create.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.feature.create.domain.Tool

/** A tool as a tile shows it. */
@Immutable
data class ToolUi(val tool: Tool, @param:StringRes val title: Int, val coverSeed: String)

fun Tool.toUi(): ToolUi = ToolUi(tool = this, title = titleRes, coverSeed = name)

@get:StringRes
val Tool.titleRes: Int
    get() = when (this) {
        Tool.VoiceToSong -> R.string.tool_voice_to_song
        Tool.Genreshift -> R.string.tool_genreshift
        Tool.SampleToSong -> R.string.tool_sample_to_song
        Tool.Unplugged -> R.string.tool_unplugged
    }
