package com.illiamelnyk.studio.feature.create.presentation.tool

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.recording.AudioReferencePicker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tool's screen, wired to its ViewModel.
 *
 * @param onClose leaves the screen.
 * @param contentPadding the space the system bars and the mini player take over the content.
 */
@Composable
fun ToolRoute(
    tool: Tool,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ToolViewModel = koinViewModel { parametersOf(tool) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(ToolIntent.GenerationOptionsRefresh) }
    val feedback: ToastController = koinInject()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ToolEffect.Close -> onClose()

            is ToolEffect.ShowToast -> feedback.show(
                effect.toast.title,
                effect.toast.message,
                isError =
                    effect.toast.kind == StudioToastKind.Error,
            )
        }
    }
    AudioReferencePicker(
        visible = state.recordingOpen,
        onSelect = { viewModel.onIntent(ToolIntent.RecordingSelected(it)) },
        onDismiss = { viewModel.onIntent(ToolIntent.RecordingDismissed) },
    )
    ToolScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
