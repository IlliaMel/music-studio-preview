package com.illiamelnyk.studio.feature.player.presentation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.playlist.PlaylistPickerRoute
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.core.presentation.asString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The full player, wired to its ViewModel.
 *
 * The state is read in two parts: the screen recomposes when the content changes, and the clock is
 * handed down as a provider that only the seek bar and the lyrics call, while they draw.
 *
 * @param onCollapse goes back to the mini player; also called when nothing is left to play.
 * @param contentPadding the space the system bars take over the player.
 * @param coverModifier makes the cover the shared element that travels from the mini player.
 * @param bodyModifier for the player's body under its sheets, such as the drag that closes it.
 */
@Composable
fun PlayerRoute(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle()
    val content by remember { derivedStateOf { state.value.content } }
    val context = LocalContext.current
    val collapse by rememberUpdatedState(onCollapse)
    val feedback: ToastController = koinInject()
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            PlayerEffect.Collapse -> onCollapse()

            is PlayerEffect.Share -> {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, effect.text.asString(context.resources))
                effect.audio?.let { audio ->
                    send.setType("audio/mpeg")
                        .putExtra(Intent.EXTRA_STREAM, audio)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.player_share)))
            }

            is PlayerEffect.OpenPlaylistPicker -> playlistTrack = effect.track
            is PlayerEffect.ShowToast -> feedback.show(
                effect.toast.title,
                effect.toast.message,
                isError = effect.toast.kind == StudioToastKind.Error,
            )
        }
    }
    // Nothing left to show, such as after a station was stopped or when the app comes back without
    // the queue it had: back to the screens, where the mini player has gone too.
    if (content.track == null) LaunchedEffect(Unit) { collapse() }
    Box(modifier) {
        PlayerScreen(
            state = content,
            clock = { state.value.clock },
            onIntent = viewModel::onIntent,
            contentPadding = contentPadding,
            coverModifier = coverModifier,
            bodyModifier = bodyModifier,
        )
        playlistTrack?.let { track -> PlaylistPickerRoute(track, onDismiss = { playlistTrack = null }) }
    }
}
