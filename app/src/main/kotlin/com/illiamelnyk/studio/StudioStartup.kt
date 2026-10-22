package com.illiamelnyk.studio

import com.illiamelnyk.studio.core.data.local.LegacyStudioStateCorruptedException
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
    private val preserveCorruptLocalData: suspend () -> Unit = { error("Local recovery unavailable") },
) {
    private val mutableState = MutableStateFlow(StartupState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableLocalRecoveryAvailable = MutableStateFlow(false)
    val localRecoveryAvailable = mutableLocalRecoveryAvailable.asStateFlow()
    private var job: Job? = null

    init {
        retry()
    }

    fun retry() {
        start(recover = false)
    }

    /** Called only after the user confirms preservation and reset of an unreadable local journal. */
    fun recoverLocalData() {
        if (!localRecoveryAvailable.value || state.value != StartupState.Failed) return
        start(recover = true)
    }

    private fun start(recover: Boolean) {
        if (job?.isActive == true || state.value == StartupState.Ready) return
        mutableState.value = StartupState.Loading
        mutableLocalRecoveryAvailable.value = false
        job = scope.launch {
            var preserved = false
            try {
                withContext(dataDispatcher) {
                    if (recover) {
                        preserveCorruptLocalData()
                        preserved = true
                    }
                    loadData()
                }
                startPlayback()
                mutableState.value = StartupState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error)
                mutableLocalRecoveryAvailable.value = generateSequence<Throwable>(error) { it.cause }
                    .take(20).any { it is LegacyStudioStateCorruptedException } || (recover && !preserved)
                mutableState.value = StartupState.Failed
            }
        }
    }
}
