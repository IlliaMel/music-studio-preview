package com.illiamelnyk.studio.core.presentation.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.OverlaySheet
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun PlaylistPickerSheet(
    state: PlaylistPickerState,
    onIntent: (PlaylistPickerIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlaySheet(visible = true, onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
        ) {
            StudioText(state.title, StudioTheme.typography.titleMedium)
            StudioButton(
                stringResource(
                    if (state.isLiked) R.string.library_unlike else R.string.library_like,
                ),
                onClick = {
                    onIntent(PlaylistPickerIntent.LikeClicked)
                },
                enabled = !state.isBusy,
                style = StudioButtonStyle.Text,
            )
            StudioText(stringResource(R.string.library_in_playlists), StudioTheme.typography.bodyMedium)
            state.playlists.forEach { playlist ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(playlist.coverSeed, StudioTheme.dimens.size.cover.m)
                    StudioText(playlist.name, StudioTheme.typography.bodyMedium, Modifier.weight(1f))
                    StudioButton(
                        stringResource(
                            if (playlist.containsTrack) R.string.library_remove else R.string.library_add,
                        ),
                        onClick = {
                            onIntent(PlaylistPickerIntent.TogglePlaylist(playlist.id))
                        },
                        enabled = !state.isBusy,
                        style = StudioButtonStyle.Text,
                    )
                }
            }
            if (state.failed) {
                StudioText(
                    stringResource(R.string.library_action_error),
                    StudioTheme.typography.bodySmall,
                    color = StudioTheme.colors.error.content,
                )
            }
            if (state.isCreating) {
                StudioTextField(
                    state.name,
                    onValueChange = {
                        onIntent(PlaylistPickerIntent.NameChanged(it))
                    },
                    placeholder = stringResource(
                        R.string.library_playlist_name,
                    ),
                    enabled = !state.isBusy,
                    errorText = if (state.nameInvalid) {
                        stringResource(
                            R.string.library_name_error,
                        )
                    } else {
                        null
                    },
                )
                StudioButton(stringResource(R.string.library_create), onClick = {
                    onIntent(PlaylistPickerIntent.Create)
                }, enabled = !state.isBusy)
            } else {
                StudioButton(stringResource(R.string.library_create_playlist), onClick = {
                    onIntent(PlaylistPickerIntent.NewPlaylist)
                }, enabled = !state.isBusy)
            }
        }
    }
}
