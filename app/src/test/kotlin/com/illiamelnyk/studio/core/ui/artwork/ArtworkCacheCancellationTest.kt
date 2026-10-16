package com.illiamelnyk.studio.core.ui.artwork

import com.illiamelnyk.studio.core.artwork.ArtworkAtlas
import com.illiamelnyk.studio.core.artwork.ArtworkAtlasLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ArtworkCacheCancellationTest {
    private val assets get() = RuntimeEnvironment.getApplication().assets

    @Test
    fun cancelledAtlasCallerCannotKeepTheCacheLockedOnAnAbandonedDispatcher() {
        cancelledCallerDoesNotPoison { atlas, dispatcher ->
            CacheProbe({ atlas.load(assets, dispatcher) }, { atlas.current })
        }
    }

    @Test
    fun cancelledUiCallerCannotKeepEitherSharedArtworkLayerLocked() {
        cancelledCallerDoesNotPoison { atlas, dispatcher ->
            val library = ArtworkLibraryLoader { manager, worker -> atlas.load(manager, worker) }
            CacheProbe({ library.load(assets, dispatcher) }, { library.current })
        }
    }

    private fun <T : Any> cancelledCallerDoesNotPoison(
        create: (ArtworkAtlasLoader, CoroutineDispatcher) -> CacheProbe<T>,
    ) {
        val callerScheduler = TestCoroutineScheduler()
        val caller = CoroutineScope(SupervisorJob() + StandardTestDispatcher(callerScheduler))
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val enteredRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val reads = AtomicInteger()
        val atlas = ArtworkAtlasLoader { manager ->
            reads.incrementAndGet()
            enteredRead.countDown()
            check(releaseRead.await(5, TimeUnit.SECONDS)) { "Test decode was not released" }
            ArtworkAtlas.read(manager)
        }
        val cache = create(atlas, worker)
        try {
            val first = caller.async { cache.load() }
            callerScheduler.runCurrent()
            assertTrue(enteredRead.await(5, TimeUnit.SECONDS))
            first.cancel()
            releaseRead.countDown()

            val restored = runBlocking { withTimeout(5_000) { requireNotNull(cache.load()) } }
            val reused = runBlocking { withTimeout(5_000) { requireNotNull(cache.load()) } }

            assertTrue(first.isCancelled)
            assertSame(restored, reused)
            assertSame(restored, cache.current())
            assertEquals(1, reads.get())
            assertEquals("studio-original-artwork-v1", requireNotNull(atlas.current).version)
            assertEquals(0L, callerScheduler.currentTime)
        } finally {
            releaseRead.countDown()
            caller.cancel()
            callerScheduler.runCurrent()
            worker.close()
        }
    }

    private data class CacheProbe<T>(val load: suspend () -> T?, val current: () -> T?)
}
