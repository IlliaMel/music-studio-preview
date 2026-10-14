package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.feature.create.domain.Tool
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The Create tab: tools, quick demo generations and the user's tracks.
 *
 * @param playingTrackId the track the player has loaded, marked in the list.
 * @param isSubmitting a demo generation is being sent; its buttons wait.
 * @param notice why the last action failed, until the next one.
 */
@Immutable
data class CreateState(
    val tools: ImmutableList<ToolUi> = Tool.entries.map(Tool::toUi).toImmutableList(),
    val tracks: ImmutableList<TrackUi> = persistentListOf(),
    val playingTrackId: TrackId? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val notice: UiText? = null,
)

sealed interface CreateIntent {
    data class ToolClicked(val tool: Tool) : CreateIntent

    data class TrackClicked(val id: TrackId) : CreateIntent

    data class RetryClicked(val id: TrackId) : CreateIntent

    /** Generate a track from a sample prompt; [nameAnArtist] picks one the service rejects. */
    data class GenerateDemoClicked(val nameAnArtist: Boolean) : CreateIntent
}

sealed interface CreateEffect {
    data class OpenTool(val tool: Tool) : CreateEffect
}
