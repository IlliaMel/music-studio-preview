package com.illiamelnyk.studio.core.data.generation

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.illiamelnyk.studio.core.data.local.StudioSynchronization

internal fun generationWorker(context: Context, synchronization: StudioSynchronization): GenerationSyncWorker =
    TestListenableWorkerBuilder<GenerationSyncWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == GenerationSyncWorker::class.java.name) {
                GenerationSyncWorker(appContext, workerParameters, synchronization)
            } else {
                null
            }
        }).build()
