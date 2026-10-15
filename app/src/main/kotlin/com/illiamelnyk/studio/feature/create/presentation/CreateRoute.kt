package com.illiamelnyk.studio.feature.create.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.audio.AudioActions
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.recording.AudioReferencePicker
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Create tab, wired to its ViewModel.
 *
 * @param onOpenTool navigates to a tool's screen.
 * @param contentPadding the space the app's bars and the system bars take over the content.
 */
@Composable
fun CreateRoute(
    onOpenTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CreateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val feedback: ToastController = koinInject()
    val audioActions: AudioActions = koinInject()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            CreateEffect.RequestNotifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            CreateEffect.OpenTools -> onOpenTools()

            CreateEffect.OpenPlayer -> onOpenPlayer()

            is CreateEffect.OpenTool -> onOpenTool(effect.tool)

            is CreateEffect.ShowToast -> feedback.show(
                effect.toast.title,
                effect.toast.message,
                isError =
                    effect.toast.kind == StudioToastKind.Error,
            )

            is CreateEffect.ShareTrack -> scope.launch {
                try {
                    if (effect.track != null) {
                        audioActions.share(context, effect.track)
                    } else {
                        context.shareTrack(effect.title, effect.prompt)
                    }
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException,
                ) {
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    feedback.show(
                        UiText.Resource(R.string.create_toast_error),
                        UiText.Resource(R.string.error_network),
                        isError = true,
                    )
                }
            }

            is CreateEffect.DownloadAudio -> scope.launch {
                try {
                    audioActions.download(context, effect.track)
                    feedback.show(UiText.Resource(R.string.player_download_saved), UiText.Raw(effect.track.title))
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException,
                ) {
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    feedback.show(
                        UiText.Resource(R.string.create_toast_error),
                        UiText.Resource(R.string.error_network),
                        isError = true,
                    )
                }
            }

            CreateEffect.TracksStarted -> {
                // The keyboard goes, and the list shows where the new tracks are.
                focusManager.clearFocus()
                scope.launch { listState.revealTracks() }
            }
        }
    }
    AudioReferencePicker(
        visible = state.sheet == CreateSheet.Reference,
        onSelect = { viewModel.onIntent(CreateIntent.ReferenceSelected(it.uri, it.title)) },
        onSave = { viewModel.onIntent(CreateIntent.RecordingSaved(it.uri, it.title, it.durationMs)) },
        onDismiss = { viewModel.onIntent(CreateIntent.SheetDismissed) },
    )
    CreateScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
        listState = listState,
    )
}

/** Reveals new tracks even when the expanded composer leaves only a short feed viewport. */
private suspend fun LazyListState.revealTracks() {
    if (firstVisibleItemIndex != CreateFeedItems.TRACKS_HEADER || firstVisibleItemScrollOffset != 0) {
        animateScrollToItem(CreateFeedItems.TRACKS_HEADER)
    }
}

private fun Context.shareTrack(title: String, prompt: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, getString(R.string.create_share_text, title, prompt))
    startActivity(Intent.createChooser(send, getString(R.string.create_share_title)))
}
