package com.illiamelnyk.studio.core.data.generation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.illiamelnyk.studio.core.data.local.StudioSynchronization
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

class GenerationSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val synchronization: StudioSynchronization,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        activeWorkers.incrementAndGet()
        return try {
            val sync = synchronization
            val pendingDeletion = when (val result = sync.recoverPendingDeletions()) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> result.error != AppError.Unauthorized
            }
            val owner = sync.activeAccountId ?: return if (pendingDeletion) Result.retry() else Result.success()
            val outcome = sync.syncPending(owner)
            // Ownership may change while a server request is finishing.
            // Resolve the current owner on the next attempt rather than declaring it synchronized.
            if (sync.activeAccountId?.let { it != owner } == true) return Result.retry()
            when (outcome) {
                is Outcome.Success -> if (outcome.value || pendingDeletion) Result.retry() else Result.success()

                is Outcome.Failure -> if (outcome.error == AppError.Unauthorized && !pendingDeletion) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            activeWorkers.decrementAndGet()
        }
    }

    companion object {
        // A worker that finishes its owner's work calls complete(); the scheduler must not cancel
        // the chain from inside it, or the successor it just asked for with Result.retry() is lost.
        private val activeWorkers = AtomicInteger()
        internal fun isRunning(): Boolean = activeWorkers.get() > 0
    }
}
