package com.illiamelnyk.studio.core.data.generation

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import com.illiamelnyk.studio.core.data.local.StudioSynchronization
import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidGenerationWorkSchedulerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var work: WorkManager
    private var workerSync: StudioSynchronization? = null

    @Before
    fun initializeWorkManager() {
        context.getSystemService(JobScheduler::class.java).cancelAll()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? = if (workerClassName == GenerationSyncWorker::class.java.name) {
                    GenerationSyncWorker(appContext, workerParameters, checkNotNull(workerSync))
                } else {
                    null
                }
            }).build(),
        )
        work = WorkManager.getInstance(context)
    }

    @After
    fun closeWorkManager() {
        runBlocking { AndroidGenerationWorkScheduler.awaitIdle() }
        work.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
        context.getSystemService(JobScheduler::class.java).cancelAll()
    }

    @Test
    fun retiresOnlyTheLegacyServiceAtItsOriginalJobId() {
        val jobs = context.getSystemService(JobScheduler::class.java)
        val legacyService = ComponentName(
            context.packageName,
            "com.illiamelnyk.studio.core.data.generation.GenerationSyncJobService",
        )
        val legacy = JobInfo.Builder(1101, legacyService).setMinimumLatency(10_000).build()
        val other = JobInfo.Builder(1202, legacyService).setMinimumLatency(20_000).build()
        assertEquals(JobScheduler.RESULT_SUCCESS, jobs.schedule(legacy))
        assertEquals(JobScheduler.RESULT_SUCCESS, jobs.schedule(other))
        AndroidGenerationWorkScheduler(context)
        assertEquals(null, jobs.getPendingJob(1101))
        assertEquals(other, jobs.getPendingJob(1202))
    }

    @Test
    fun legacyIdCollisionNeverCancelsWorkManagerOrAnUnrelatedService() {
        val jobs = context.getSystemService(JobScheduler::class.java)
        for (serviceName in listOf(
            "androidx.work.impl.background.systemjob.SystemJobService",
            "com.illiamelnyk.studio.other.BackgroundService",
        )) {
            val owner = ComponentName(context.packageName, serviceName)
            val collision = JobInfo.Builder(1101, owner).setMinimumLatency(10_000).build()
            val other = JobInfo.Builder(1202, owner).setMinimumLatency(20_000).build()
            assertEquals(JobScheduler.RESULT_SUCCESS, jobs.schedule(collision))
            assertEquals(JobScheduler.RESULT_SUCCESS, jobs.schedule(other))
            AndroidGenerationWorkScheduler(context)
            AndroidGenerationWorkScheduler(context)
            assertEquals(collision, jobs.getPendingJob(1101))
            assertEquals(other, jobs.getPendingJob(1202))
        }
    }

    @Test
    fun pendingWorkSurvivesSchedulerRecreationAndRequiresNetworkWithoutPersistingIdentity() {
        AndroidGenerationWorkScheduler(context).reconcile("owner-one")
        val original = active().single()
        AndroidGenerationWorkScheduler(context).reconcile("owner-one")
        assertEquals(original.id, active().single().id)
        val spec = runBlocking(Dispatchers.IO) {
            (work as WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(original.id.toString())!!
        }
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(10_000L, spec.initialDelay)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(10_000L, spec.backoffDelayDuration)
        assertTrue(spec.input.keyValueMap.isEmpty())
    }

    @Test
    fun ownerSwitchRetainsOneNeutralJobAndSignOutCancelsIt() {
        val scheduler = AndroidGenerationWorkScheduler(context)
        scheduler.reconcile("owner-one")
        val original = active().single().id
        scheduler.reconcile("owner-two")
        assertEquals(original, active().single().id)
        scheduler.reconcile(null)
        assertTrue(active().isEmpty())
        assertEquals(WorkInfo.State.CANCELLED, work.getWorkInfoById(original).get()!!.state)
        scheduler.reconcile("owner-three")
        assertFalse(original == active().single().id)
    }

    @Test
    fun completingIdleWorkCancelsItAndDeletionRecoveryCanEnqueueWithoutAnAccount() {
        val scheduler = AndroidGenerationWorkScheduler(context)
        scheduler.reconcile("account-deletion")
        assertEquals(1, active().size)
        scheduler.complete("account-deletion")
        assertTrue(active().isEmpty())
    }

    @Test
    fun runningRecoveryCompletesWithoutCancelingItsOwnWork() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scheduler = AndroidGenerationWorkScheduler(context)
        workerSync = object : StudioSynchronization {
            override val activeAccountId: String? = null
            override suspend fun recoverPendingDeletions(): Outcome<Boolean> {
                entered.complete(Unit)
                release.await()
                scheduler.complete("account-deletion")
                return Outcome.Success(false)
            }
            override suspend fun syncPending(ownerId: String): Outcome<Boolean> = error("No active owner")
            override fun setForeground(active: Boolean) = Unit
        }
        scheduler.reconcile("account-deletion")
        val id = active().single().id
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setInitialDelayMet(id)
        driver.setAllConstraintsMet(id)
        entered.await()
        assertTrue(GenerationSyncWorker.isRunning())
        scheduler.complete("account-deletion")
        assertEquals(WorkInfo.State.RUNNING, work.getWorkInfoById(id).get()!!.state)
        release.complete(Unit)
        val terminal = work.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true }!!
        assertEquals(WorkInfo.State.SUCCEEDED, terminal.state)
        assertFalse(GenerationSyncWorker.isRunning())
    }

    @Test
    fun signOutCancelsActualRunningWorkerAndPropagatesCancellation() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val scheduler = AndroidGenerationWorkScheduler(context)
        workerSync = object : StudioSynchronization {
            override val activeAccountId: String = "owner"
            override suspend fun recoverPendingDeletions(): Outcome<Boolean> = Outcome.Success(false)
            override suspend fun syncPending(ownerId: String): Outcome<Boolean> {
                entered.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                    error("Unreachable")
                } finally {
                    cancelled.complete(Unit)
                }
            }
            override fun setForeground(active: Boolean) = Unit
        }
        scheduler.reconcile("owner")
        val id = active().single().id
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setInitialDelayMet(id)
        driver.setAllConstraintsMet(id)
        entered.await()
        scheduler.reconcile(null)
        cancelled.await()
        val terminal = work.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true }!!
        assertEquals(WorkInfo.State.CANCELLED, terminal.state)
        assertTrue(active().isEmpty())
    }

    @Test
    fun repeatedReconcileWhileRunningCreatesOnlyOneSuccessorAndCompletionRetainsIt() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scheduler = AndroidGenerationWorkScheduler(context)
        workerSync = object : StudioSynchronization {
            override val activeAccountId: String = "owner"
            override suspend fun recoverPendingDeletions(): Outcome<Boolean> = Outcome.Success(false)
            override suspend fun syncPending(ownerId: String): Outcome<Boolean> {
                entered.complete(Unit)
                release.await()
                // Simulate the old owner's last transaction reporting completion.
                scheduler.complete(ownerId)
                return Outcome.Success(false)
            }
            override fun setForeground(active: Boolean) = Unit
        }
        scheduler.reconcile("owner")
        val original = active().single().id
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setInitialDelayMet(original)
        driver.setAllConstraintsMet(original)
        entered.await()
        repeat(20) { scheduler.reconcile("latest-owner") }
        AndroidGenerationWorkScheduler(context).reconcile("latest-owner")
        AndroidGenerationWorkScheduler.awaitIdle()
        val chain = active()
        assertEquals(2, chain.size)
        assertEquals(1, chain.count { it.state == WorkInfo.State.RUNNING })
        assertEquals(1, chain.count { it.state == WorkInfo.State.BLOCKED })
        val successor = chain.single { it.id != original }.id
        release.complete(Unit)
        assertEquals(
            WorkInfo.State.SUCCEEDED,
            work.getWorkInfoByIdFlow(original).first {
                it?.state?.isFinished == true
            }!!.state,
        )
        assertEquals(successor, active().single().id)
        assertEquals(WorkInfo.State.ENQUEUED, active().single().state)
    }

    @Test
    fun reconcileAtTheWorkerCompletionBoundaryCannotBeLost() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val scheduler = AndroidGenerationWorkScheduler(context)
        workerSync = object : StudioSynchronization {
            override val activeAccountId: String = "owner"
            override suspend fun recoverPendingDeletions(): Outcome<Boolean> = Outcome.Success(false)
            override suspend fun syncPending(ownerId: String): Outcome<Boolean> {
                entered.complete(Unit)
                finish.await()
                // A new durable operation requests work just before this attempt returns success.
                scheduler.reconcile("owner")
                AndroidGenerationWorkScheduler.awaitIdle()
                scheduler.complete("owner")
                return Outcome.Success(false)
            }
            override fun setForeground(active: Boolean) = Unit
        }
        scheduler.reconcile("owner")
        val original = active().single().id
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setInitialDelayMet(original)
        driver.setAllConstraintsMet(original)
        entered.await()
        finish.complete(Unit)
        assertEquals(
            WorkInfo.State.SUCCEEDED,
            work.getWorkInfoByIdFlow(original).first {
                it?.state?.isFinished == true
            }!!.state,
        )
        val remaining = active().single()
        assertFalse(remaining.id == original)
        assertEquals(WorkInfo.State.ENQUEUED, remaining.state)
    }

    private fun active(): List<WorkInfo> {
        runBlocking { AndroidGenerationWorkScheduler.awaitIdle() }
        return work.getWorkInfosForUniqueWork(AndroidGenerationWorkScheduler.WORK_NAME).get()
            .filterNot { it.state.isFinished }
    }
}
