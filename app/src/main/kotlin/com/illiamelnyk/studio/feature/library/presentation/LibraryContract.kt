package com.illiamelnyk.studio.feature.library.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class LibraryTab { Liked, Playlists }

@Immutable
data class PlaylistUi(
    val id: String,
    val name: String,
    val tracks: ImmutableList<TrackUi>,
    val subtitle: UiText = UiText.Raw(""),
    val coverSeed: String = name,
)

@Immutable
data class LibraryState(
    val liked: ImmutableList<TrackUi> = persistentListOf(),
    val playlists: ImmutableList<PlaylistUi> = persistentListOf(),
    val tab: LibraryTab = LibraryTab.Liked,
    val selectedPlaylistId: String? = null,
    val pickerTrack: Track? = null,
    val playlistName: String = "",
    val showCreatePlaylist: Boolean = false,
    val nameInvalid: Boolean = false,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
    val playingTrackId: TrackId? = null,
    val isLoading: Boolean = true,
)

sealed interface LibraryIntent {
    data object BackClicked : LibraryIntent
    data object ExploreClicked : LibraryIntent
    data class TrackClicked(val id: TrackId) : LibraryIntent
    data class TrackMoreClicked(val id: TrackId) : LibraryIntent
    data object DismissPicker : LibraryIntent
    data class TabSelected(val tab: LibraryTab) : LibraryIntent
    data class PlaylistSelected(val id: String?) : LibraryIntent
    data class PlayPlaylist(val shuffle: Boolean) : LibraryIntent
    data object NewPlaylist : LibraryIntent
    data object DismissCreate : LibraryIntent
    data class PlaylistNameChanged(val name: String) : LibraryIntent
    data object CreatePlaylist : LibraryIntent
}

sealed interface LibraryEffect {
    data object Close : LibraryEffect
    data object Explore : LibraryEffect
    data object OpenPlayer : LibraryEffect
}
