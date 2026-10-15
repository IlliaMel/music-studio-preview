package com.illiamelnyk.studio.feature.create.presentation.recording

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.feature.create.domain.RecordedAudio
import com.illiamelnyk.studio.feature.create.presentation.common.OverlaySheet
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AudioReferencePicker(
    visible: Boolean,
    onSelect: (RecordedAudio) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: ((RecordedAudio) -> Unit)? = null,
) {
    if (!visible) return
    val viewModel: RecorderViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember { MediaPlayer() }
    val playback: PlaybackController = koinInject()
    fun pauseMusic() {
        if (playback.state.value.isPlaying) playback.togglePlayPause()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onIntent(if (granted) RecorderIntent.Start else RecorderIntent.PermissionDenied)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onIntent(RecorderIntent.Import(uri.toString()))
    }
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onIntent(RecorderIntent.Finish)
                player.reset()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.onIntent(RecorderIntent.Finish)
            player.release()
        }
    }
    OverlaySheet(visible = true, onDismissRequest = onDismiss) {
        RecorderPanel(
            state = state,
            onRecord = {
                player.reset()
                pauseMusic()
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.onIntent(RecorderIntent.Start)
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onPauseResume = { viewModel.onIntent(if (state.paused) RecorderIntent.Resume else RecorderIntent.Pause) },
            onFinish = { viewModel.onIntent(RecorderIntent.Finish) },
            onPick = {
                picker.launch(
                    arrayOf(
                        "audio/wav", "audio/x-wav", "audio/wave",
                        "audio/mp4", "audio/x-m4a", "audio/mpeg",
                        "audio/aac", "audio/ogg", "application/ogg",
                        "audio/webm",
                    ),
                )
            },
            onTitleChange = { viewModel.onIntent(RecorderIntent.TitleChanged(it)) },
            onPreview = {
                state.audio?.let { audio ->
                    pauseMusic()
                    runCatching {
                        player.reset()
                        player.setDataSource(context, audio.uri.toUri())
                        player.setOnPreparedListener { prepared ->
                            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                                !viewModel.state.value.recording
                            ) {
                                prepared.start()
                            } else {
                                prepared.reset()
                            }
                        }
                        player.setOnErrorListener { _, _, _ ->
                            viewModel.onIntent(RecorderIntent.PreviewFailed)
                            true
                        }
                        player.prepareAsync()
                    }.onFailure { viewModel.onIntent(RecorderIntent.PreviewFailed) }
                }
            },
            onUse = { state.audio?.let(onSelect) },
            onSave = onSave?.let { save -> { state.audio?.let(save) } },
            modifier = modifier,
        )
    }
}
