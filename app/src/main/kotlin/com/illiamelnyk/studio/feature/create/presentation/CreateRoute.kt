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
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.domain.Tool
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

            is CreateEffect.ShareTrack -> context.shareTrack(effect.title, effect.prompt)

            CreateEffect.TracksStarted -> {
                // The keyboard goes, and the list shows where the new tracks are.
                focusManager.clearFocus()
                scope.launch { listState.revealTracks() }
            }
        }
    }
    CreateScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
        listState = listState,
    )
}

/** Scrolls up to "Your tracks" when the list is past it, where new tracks would be out of sight. */
private suspend fun LazyListState.revealTracks() {
    if (firstVisibleItemIndex > CreateFeedItems.TRACKS_HEADER) animateScrollToItem(CreateFeedItems.TRACKS_HEADER)
}

private fun Context.shareTrack(title: String, prompt: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, getString(R.string.create_share_text, title, prompt))
    startActivity(Intent.createChooser(send, getString(R.string.create_share_title)))
}
