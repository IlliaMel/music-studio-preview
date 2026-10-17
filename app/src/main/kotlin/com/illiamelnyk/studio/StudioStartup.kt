package com.illiamelnyk.studio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class StartupState { Loading, Ready, Failed }

internal class StudioStartup(
    private val scope: CoroutineScope,
    private val dataDispatcher: CoroutineDispatcher,
    private val loadData: suspend () -> Unit,
    private val startPlayback: () -> Unit,
    private val onFailure: (Exception) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(StartupState.Loading)
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        retry()
    }

    fun retry() {
        if (job?.isActive == true || state.value == StartupState.Ready) return
        mutableState.value = StartupState.Loading
        job = scope.launch {
            try {
                withContext(dataDispatcher) { loadData() }
                startPlayback()
                mutableState.value = StartupState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error)
                mutableState.value = StartupState.Failed
            }
        }
    }
}
