package com.illiamelnyk.studio.core.presentation.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.domain.track.Track
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Shared by Player, Library and collection track menus. */
@Composable
fun PlaylistPickerRoute(track: Track, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PlaylistPickerViewModel =
        koinViewModel(key = "playlist-picker-" + track.id.value, parameters = { parametersOf(track) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaylistPickerSheet(state, viewModel::onIntent, onDismiss, modifier)
}
