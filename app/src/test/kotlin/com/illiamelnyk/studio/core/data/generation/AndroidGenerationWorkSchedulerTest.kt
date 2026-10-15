package com.illiamelnyk.studio.core.data.generation

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidGenerationWorkSchedulerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val jobs = context.getSystemService(JobScheduler::class.java)

    @Before
    fun cleanJobs() {
        jobs.cancelAll()
    }

    @Test
    fun pendingWorkSurvivesSchedulerRecreationAndRequiresNetworkWithoutPersistingTokens() {
        AndroidGenerationWorkScheduler(context).reconcile("owner-one")
        val original = jobs.getPendingJob(AndroidGenerationWorkScheduler.JOB_ID)!!
        AndroidGenerationWorkScheduler(context).reconcile("owner-one")
        val restored = jobs.getPendingJob(AndroidGenerationWorkScheduler.JOB_ID)!!

        assertEquals(1, jobs.allPendingJobs.size)
        assertEquals(original, restored)
        assertTrue(restored.isPersisted)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, restored.networkType)
        assertEquals(10_000L, restored.minLatencyMillis)
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, restored.backoffPolicy)
        assertEquals("owner-one", restored.extras.getString(AndroidGenerationWorkScheduler.OWNER))
        assertEquals(setOf(AndroidGenerationWorkScheduler.OWNER), restored.extras.keySet())
    }

    @Test
    fun ownerSwitchReplacesTheSingleJobAndSignOutRemovesIt() {
        val scheduler = AndroidGenerationWorkScheduler(context)
        scheduler.reconcile("owner-one")
        scheduler.reconcile("owner-two")
        assertEquals(1, jobs.allPendingJobs.size)
        assertEquals(
            "owner-two",
            jobs.getPendingJob(AndroidGenerationWorkScheduler.JOB_ID)!!.extras
                .getString(AndroidGenerationWorkScheduler.OWNER),
        )
        assertFalse(jobs.allPendingJobs.isEmpty())
        scheduler.reconcile(null)
        assertNull(jobs.getPendingJob(AndroidGenerationWorkScheduler.JOB_ID))
    }

    @Test
    fun terminalRunningJobStaysAliveForCompletionDeliveryButOwnerLossCancelsImmediately() {
        val scheduler = AndroidGenerationWorkScheduler(context)
        scheduler.reconcile("owner-one")
        GenerationSyncJobService.runningOwner = "owner-one"
        try {
            scheduler.complete("owner-one")
            assertTrue(jobs.getPendingJob(AndroidGenerationWorkScheduler.JOB_ID) != null)
            scheduler.reconcile(null)
            assertNull(jobs.getPendingJob(AndroidGenerationWorkScheduler.JOB_ID))
        } finally {
            GenerationSyncJobService.runningOwner = null
        }
    }
}
