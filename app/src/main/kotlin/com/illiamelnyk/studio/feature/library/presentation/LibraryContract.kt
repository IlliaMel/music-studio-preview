package com.illiamelnyk.studio.feature.library.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Library: the tracks the user liked. */
@Immutable
data class LibraryState(
    val liked: ImmutableList<TrackUi> = persistentListOf(),
    val playingTrackId: TrackId? = null,
    val isLoading: Boolean = true,
)

sealed interface LibraryIntent {
    data object BackClicked : LibraryIntent

    data class TrackClicked(val id: TrackId) : LibraryIntent
}

sealed interface LibraryEffect {
    data object Close : LibraryEffect
}
