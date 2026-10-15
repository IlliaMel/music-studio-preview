package com.illiamelnyk.studio.feature.library.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import kotlinx.collections.immutable.persistentListOf

/** Library with liked tracks, with none, and while they load. */
class LibraryPreviewData : PreviewParameterProvider<LibraryState> {
    override val values = sequenceOf(
        LibraryState(
            liked = persistentListOf(
                track("1", "Rainy window lo-fi", "Illia Melnyk · 2:00"),
                track("2", "Neon tram", "Illia Melnyk · 2:30"),
            ),
            playingTrackId = TrackId("2"),
            isLoading = false,
        ),
        LibraryState(isLoading = false),
        LibraryState(isLoading = true),
        LibraryState(tab = LibraryTab.Playlists, isLoading = false),
        LibraryState(
            tab = LibraryTab.Playlists,
            playlists = persistentListOf(
                PlaylistUi("mix", "Night drive", persistentListOf(track("1", "Rainy window lo-fi", "Studio · 2:00"))),
            ),
            isLoading = false,
        ),
    )

    private fun track(id: String, title: String, subtitle: String) = TrackUi(
        id = TrackId(id),
        title = title,
        subtitle = UiText.Raw(subtitle),
        coverSeed = title,
        status = TrackUi.Status.Ready,
    )
}
