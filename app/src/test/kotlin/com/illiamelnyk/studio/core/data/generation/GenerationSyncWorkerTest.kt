package com.illiamelnyk.studio.core.data.generation

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.illiamelnyk.studio.core.data.local.StudioSynchronization
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GenerationSyncWorkerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun recoveryRunsBeforeResolvingCurrentOwner() = runTest {
        val sync = SyncFake("stale-owner")
        sync.recover = {
            sync.owner = "current-owner"
            Outcome.Success(false)
        }
        assertEquals(Result.success(), worker(sync).doWork())
        assertEquals(listOf("recover", "sync:current-owner"), sync.calls)
    }

    @Test
    fun noAccountStillRetriesDurableDeletionAndCompletesWhenRecoveryFinishes() = runTest {
        val sync = SyncFake(null)
        sync.recover = { Outcome.Success(true) }
        assertEquals(Result.retry(), worker(sync).doWork())
        sync.recover = { Outcome.Success(false) }
        assertEquals(Result.success(), worker(sync).doWork())
        assertEquals(listOf("recover", "recover"), sync.calls)
    }

    @Test
    fun pendingGenerationOrDeletionRetriesAndTerminalWorkCompletes() = runTest {
        val sync = SyncFake("owner")
        sync.pending = { Outcome.Success(true) }
        assertEquals(Result.retry(), worker(sync).doWork())
        sync.pending = { Outcome.Success(false) }
        sync.recover = { Outcome.Success(true) }
        assertEquals(Result.retry(), worker(sync).doWork())
        sync.recover = { Outcome.Success(false) }
        assertEquals(Result.success(), worker(sync).doWork())
    }

    @Test
    fun recoverableFailuresRetryButUnauthorizedGenerationDoesNotSpin() = runTest {
        val sync = SyncFake("owner")
        for (error in listOf(AppError.Network, AppError.Server, AppError.Unknown)) {
            sync.pending = { Outcome.Failure(error) }
            assertEquals(Result.retry(), worker(sync).doWork())
        }
        sync.pending = { Outcome.Failure(AppError.Unauthorized) }
        assertEquals(Result.success(), worker(sync).doWork())
        sync.recover = { Outcome.Success(true) }
        assertEquals(Result.retry(), worker(sync).doWork())
        sync.owner = null
        sync.recover = { Outcome.Failure(AppError.Unauthorized) }
        assertEquals(Result.success(), worker(sync).doWork())
    }

    @Test
    fun unexpectedRecoveryOrGenerationFailureRetries() = runTest {
        val sync = SyncFake("owner")
        sync.recover = { throw java.io.IOException("storage unavailable") }
        assertEquals(Result.retry(), worker(sync).doWork())
        sync.recover = { Outcome.Success(false) }
        sync.pending = { throw java.io.IOException("transport unavailable") }
        assertEquals(Result.retry(), worker(sync).doWork())
        assertFalse(GenerationSyncWorker.isRunning())
    }

    @Test
    fun koinFactoryConstructsWorkerWithItsSynchronizationDependency() = runTest {
        val sync = SyncFake("injected-owner")
        startKoin {
            androidContext(context)
            modules(
                module {
                    single<StudioSynchronization> { sync }
                    workerOf(::GenerationSyncWorker)
                },
            )
        }
        try {
            val worker = TestListenableWorkerBuilder<GenerationSyncWorker>(context)
                .setWorkerFactory(KoinWorkerFactory()).build()
            assertEquals(Result.success(), worker.doWork())
            assertEquals(listOf("recover", "sync:injected-owner"), sync.calls)
            assertFalse(GenerationSyncWorker.isRunning())
        } finally {
            stopKoin()
        }
    }

    @Test
    fun ownerLossDuringRecoveryNeverStartsGeneration() = runTest {
        val sync = SyncFake("owner")
        sync.recover = {
            sync.owner = null
            Outcome.Success(false)
        }
        assertEquals(Result.success(), worker(sync).doWork())
        assertEquals(listOf("recover"), sync.calls)
    }

    @Test
    fun ownerSwitchWhileOldSynchronizationFinishesKeepsTheNeutralRequestRetryable() = runTest {
        val sync = SyncFake("old-owner")
        sync.pending = {
            sync.owner = "new-owner"
            Outcome.Success(false)
        }
        assertEquals(Result.retry(), worker(sync).doWork())
        assertEquals(listOf("recover", "sync:old-owner"), sync.calls)
        sync.pending = { Outcome.Success(false) }
        assertEquals(Result.success(), worker(sync).doWork())
        assertEquals("sync:new-owner", sync.calls.last())
    }

    @Test
    fun cancellationPropagatesAndClearsRunningRegistration() = runTest {
        val sync = SyncFake("owner")
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Outcome<Boolean>>()
        sync.pending = {
            entered.complete(Unit)
            never.await()
        }
        val task = async { worker(sync).doWork() }
        entered.await()
        assertTrue(GenerationSyncWorker.isRunning())
        task.cancel()
        try {
            task.await()
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(GenerationSyncWorker.isRunning())
        }
    }

    @Test
    fun cancellationDuringRecoveryAlsoPropagates() = runTest {
        val sync = SyncFake("owner")
        sync.recover = { throw CancellationException("stopped") }
        try {
            worker(sync).doWork()
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(GenerationSyncWorker.isRunning())
        }
    }

    private fun worker(sync: StudioSynchronization): GenerationSyncWorker = generationWorker(context, sync)

    private class SyncFake(var owner: String?) : StudioSynchronization {
        val calls = mutableListOf<String>()
        override val activeAccountId: String? get() = owner
        var recover: suspend () -> Outcome<Boolean> = { Outcome.Success(false) }
        var pending: suspend (String) -> Outcome<Boolean> = { Outcome.Success(false) }
        override suspend fun recoverPendingDeletions(): Outcome<Boolean> {
            calls += "recover"
            return recover()
        }
        override suspend fun syncPending(ownerId: String): Outcome<Boolean> {
            calls += "sync:" + ownerId
            return pending(ownerId)
        }
        override fun setForeground(active: Boolean) = Unit
    }
}
