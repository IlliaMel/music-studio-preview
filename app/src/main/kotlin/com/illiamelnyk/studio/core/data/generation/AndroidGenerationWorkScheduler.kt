package com.illiamelnyk.studio.core.data.generation

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import java.io.IOException

class AndroidGenerationWorkScheduler(context: Context) : GenerationWorkScheduler {
    private val scheduler = context.getSystemService(JobScheduler::class.java)
    private val component = ComponentName(context, GenerationSyncJobService::class.java)

    override fun reconcile(ownerId: String?) {
        if (ownerId == null) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID)?.extras?.getString(OWNER) == ownerId) return
        val extras = PersistableBundle().apply { putString(OWNER, ownerId) }
        val job = JobInfo.Builder(JOB_ID, component)
            .setExtras(extras)
            .setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(10_000)
            .setBackoffCriteria(10_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) throw IOException("Cannot schedule generation sync")
    }

    override fun complete(ownerId: String) {
        if (GenerationSyncJobService.runningOwner != ownerId) scheduler.cancel(JOB_ID)
    }

    companion object {
        const val JOB_ID = 1101
        const val OWNER = "ownerId"
    }
}
