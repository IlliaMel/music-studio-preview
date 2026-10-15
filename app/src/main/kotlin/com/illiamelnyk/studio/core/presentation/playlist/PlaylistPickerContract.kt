package com.illiamelnyk.studio.core.presentation.playlist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PlaylistChoice(val id: String, val name: String, val containsTrack: Boolean, val coverSeed: String = name)

@Immutable
data class PlaylistPickerState(
    val title: String = "",
    val playlists: ImmutableList<PlaylistChoice> = persistentListOf(),
    val name: String = "",
    val isCreating: Boolean = false,
    val isBusy: Boolean = false,
    val nameInvalid: Boolean = false,
    val failed: Boolean = false,
    val isLiked: Boolean = false,
)

sealed interface PlaylistPickerIntent {
    data class TogglePlaylist(val id: String) : PlaylistPickerIntent
    data object LikeClicked : PlaylistPickerIntent
    data object NewPlaylist : PlaylistPickerIntent
    data class NameChanged(val value: String) : PlaylistPickerIntent
    data object Create : PlaylistPickerIntent
}
