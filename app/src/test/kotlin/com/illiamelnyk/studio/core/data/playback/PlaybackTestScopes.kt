package com.illiamelnyk.studio.core.data.playback

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class PlaybackTestScopes(private val scheduler: TestCoroutineScheduler) {
    private val jobs = mutableListOf<Job>()
    private var queuedWorkLooper: ShadowLooper? = null
    private var originallyPaused = false

    fun wrap(parent: CoroutineScope): CoroutineScope {
        if (queuedWorkLooper == null) {
            val handler = ReflectionHelpers.callStaticMethod<Handler>(
                Class.forName("android.app.QueuedWork"),
                "getHandler",
            )
            check(handler.looper !== Looper.getMainLooper())
            queuedWorkLooper = shadowOf(handler.looper).also { looper ->
                originallyPaused = looper.isPaused
                if (originallyPaused) looper.unPause()
            }
        }
        val job = SupervisorJob(parent.coroutineContext[Job])
        jobs += job
        return CoroutineScope(parent.coroutineContext + job)
    }

    /** Cancel and await while runTest can still advance Main continuations and IO completions. */
    suspend fun closeSuspending() = withContext(NonCancellable) {
        if (jobs.isEmpty() && queuedWorkLooper == null) return@withContext
        try {
            jobs.forEach { it.cancel() }
            jobs.joinAll()
            withContext(Dispatchers.IO) {
                PlaybackPersistenceWriter.shared.flush()
                ReflectionHelpers.callStaticMethod<Any?>(Class.forName("android.app.QueuedWork"), "waitToFinish")
            }
        } finally {
            if (originallyPaused) queuedWorkLooper?.pause()
            jobs.clear()
            queuedWorkLooper = null
        }
    }

    fun close() {
        if (jobs.isEmpty() && queuedWorkLooper == null) return
        try {
            jobs.forEach { it.cancel() }
            scheduler.runCurrent()
            runBlocking { jobs.joinAll() }
            PlaybackPersistenceWriter.shared.flush()
            ReflectionHelpers.callStaticMethod<Any?>(Class.forName("android.app.QueuedWork"), "waitToFinish")
        } finally {
            if (originallyPaused) queuedWorkLooper?.pause()
            jobs.clear()
            queuedWorkLooper = null
        }
    }
}
