package com.illiamelnyk.studio.feature.create.presentation.recording

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.feature.create.domain.AudioRecorder
import com.illiamelnyk.studio.feature.create.domain.RecordedAudio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RecorderState(
    val recording: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val amplitude: Float = 0f,
    val audio: RecordedAudio? = null,
    val error: UiText? = null,
    val importing: Boolean = false,
)

sealed interface RecorderIntent {
    data object Start : RecorderIntent
    data object Pause : RecorderIntent
    data object Resume : RecorderIntent
    data object Finish : RecorderIntent
    data object PermissionDenied : RecorderIntent
    data class Import(val uri: String) : RecorderIntent
    data class TitleChanged(val title: String) : RecorderIntent
    data object PreviewFailed : RecorderIntent
}

class RecorderViewModel(private val recorder: AudioRecorder) :
    MviViewModel<RecorderState, RecorderIntent, Nothing>(RecorderState()) {
    private var clock: Job? = null

    override fun onIntent(intent: RecorderIntent) {
        if (intent is RecorderIntent.Import) {
            if (state.value.recording || state.value.importing) return
            setState { copy(importing = true, error = null) }
            viewModelScope.launch {
                try {
                    val imported = recorder.importAudio(intent.uri)
                    setState { copy(audio = imported, importing = false) }
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException,
                ) {
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    setState { copy(importing = false, error = UiText.Resource(R.string.record_error)) }
                }
            }
            return
        }
        try {
            when (intent) {
                RecorderIntent.Start -> {
                    if (state.value.recording || state.value.importing) return
                    recorder.start()
                    setState { RecorderState(recording = true) }
                    clock?.cancel()
                    clock = viewModelScope.launch {
                        while (state.value.recording) {
                            setState { copy(elapsedMs = recorder.elapsedMs(), amplitude = recorder.amplitude()) }
                            if (state.value.elapsedMs >= 120_000) onIntent(RecorderIntent.Finish)
                            delay(100)
                        }
                    }
                }

                RecorderIntent.Pause -> {
                    if (!state.value.recording || state.value.paused) return
                    recorder.pause()
                    setState { copy(paused = true) }
                }

                RecorderIntent.Resume -> {
                    if (!state.value.recording || !state.value.paused) return
                    recorder.resume()
                    setState { copy(paused = false) }
                }

                RecorderIntent.Finish -> {
                    if (!state.value.recording) return
                    clock?.cancel()
                    val audio = recorder.finish()
                    setState { copy(recording = false, paused = false, audio = audio) }
                }

                RecorderIntent.PermissionDenied -> setState {
                    copy(error = UiText.Resource(R.string.record_permission))
                }

                is RecorderIntent.TitleChanged -> setState { copy(audio = audio?.copy(title = intent.title.take(80))) }

                RecorderIntent.PreviewFailed -> setState { copy(error = UiText.Resource(R.string.record_error)) }

                is RecorderIntent.Import -> Unit
            }
        } catch (_: Exception) {
            recorder.release()
            clock?.cancel()
            setState { copy(recording = false, paused = false, error = UiText.Resource(R.string.record_error)) }
        }
    }

    override fun onCleared() {
        clock?.cancel()
        recorder.release()
    }
}
