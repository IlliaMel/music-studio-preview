package com.illiamelnyk.studio.core.data.generation

import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidGenerationWorkScheduler(context: Context) : GenerationWorkScheduler {
    private val work = WorkManager.getInstance(context.applicationContext)

    init {
        // Job IDs are app-wide; this number may now belong to WorkManager or another service.
        val jobs = context.getSystemService(JobScheduler::class.java)
        val legacyService = ComponentName(
            context.packageName,
            "com.illiamelnyk.studio.core.data.generation.GenerationSyncJobService",
        )
        if (jobs?.getPendingJob(1101)?.service == legacyService) jobs.cancel(1101)
    }

    override fun reconcile(ownerId: String?) {
        val hasPendingWork = ownerId != null
        submit {
            if (!hasPendingWork) {
                work.cancelUniqueWork(WORK_NAME).result.awaitResult()
            } else {
                val chain = work.getWorkInfosForUniqueWork(WORK_NAME).awaitResult()
                // A queued successor already observes the latest durable state when it starts.
                if (chain.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
                    work.enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        request(),
                    ).result.awaitResult()
                }
            }
        }
    }

    override fun complete(ownerId: String) {
        // Completion from inside a worker must retain its successor. Explicit signout uses reconcile(null).
        if (GenerationSyncWorker.isRunning()) return
        submit {
            val chain = work.getWorkInfosForUniqueWork(WORK_NAME).awaitResult()
            if (chain.none { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }) {
                work.cancelUniqueWork(WORK_NAME).result.awaitResult()
            }
        }
    }

    private fun request() = OneTimeWorkRequestBuilder<GenerationSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(10, TimeUnit.SECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .addTag(WORK_NAME)
        .build()

    companion object {
        const val WORK_NAME = "studio-generation-sync"

        // One process-wide actor also serializes callers that recreate the scheduler.
        // It performs no periodic work, retains no account identity, and never blocks Main.
        private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        private val actor = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (command in commands) {
                try {
                    command()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    currentCoroutineContext().ensureActive()
                    android.util.Log.w("StudioWork", "WorkManager canceled a scheduling operation")
                } catch (error: Exception) {
                    android.util.Log.w("StudioWork", "Cannot reconcile durable work: " + error.javaClass.simpleName)
                }
            }
        }

        private fun submit(command: suspend () -> Unit) {
            commands.trySend(command).getOrThrow()
        }

        internal suspend fun awaitIdle() {
            val finished = CompletableDeferred<Unit>()
            submit { finished.complete(Unit) }
            finished.await()
        }
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try {
            continuation.resume(get())
        } catch (error: ExecutionException) {
            continuation.resumeWithException(error.cause ?: error)
        } catch (error: Exception) {
            continuation.resumeWithException(error)
        }
    }, Executor { it.run() })
    continuation.invokeOnCancellation { cancel(false) }
}
