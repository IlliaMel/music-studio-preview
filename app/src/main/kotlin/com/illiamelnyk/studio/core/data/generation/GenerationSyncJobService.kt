package com.illiamelnyk.studio.core.data.generation

import android.app.job.JobParameters
import android.app.job.JobService
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class GenerationSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val owner = params.extras.getString(AndroidGenerationWorkScheduler.OWNER) ?: return false
        work?.cancel()
        runningOwner = owner
        work = scope.launch {
            val result = get<DurableStudioRepository>().syncPending(owner)
            val retry = when (result) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> result.error != AppError.Unauthorized
            }
            runningOwner = null
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        work = null
        runningOwner = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        runningOwner = null
        super.onDestroy()
    }
    companion object {
        @Volatile internal var runningOwner: String? = null
    }
}
