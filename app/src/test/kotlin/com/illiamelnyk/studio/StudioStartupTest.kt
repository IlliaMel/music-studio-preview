package com.illiamelnyk.studio

import com.illiamelnyk.studio.core.data.local.LegacyStudioStateCorruptedException
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudioStartupTest {
    @Test fun malformedLegacyRecoveryRequiresExplicitChoiceAndPreservesBeforeLoading() = runTest {
        val events = mutableListOf<String>()
        var preserved = false
        val startup = StudioStartup(backgroundScope, StandardTestDispatcher(testScheduler), {
            events += "load"
            if (!preserved) {
                throw IllegalStateException(
                    "wrapped dependency failure",
                    LegacyStudioStateCorruptedException(IOException("private fixture")),
                )
            }
        }, { events += "playback" }, preserveCorruptLocalData = {
            events += "preserve"
            preserved = true
        })
        runCurrent()
        assertEquals(StartupState.Failed, startup.state.value)
        assertTrue(startup.localRecoveryAvailable.value)
        assertEquals(listOf("load"), events)
        startup.retry()
        runCurrent()
        assertEquals(listOf("load", "load"), events)
        assertTrue(startup.localRecoveryAvailable.value)
        startup.recoverLocalData()
        startup.recoverLocalData()
        runCurrent()
        assertEquals(listOf("load", "load", "preserve", "load", "playback"), events)
        assertEquals(StartupState.Ready, startup.state.value)
        assertFalse(startup.localRecoveryAvailable.value)
    }

    @Test fun arbitraryStartupErrorsNeverOfferOrExecuteLocalReset() = runTest {
        var recoveries = 0
        val startup = StudioStartup(backgroundScope, StandardTestDispatcher(testScheduler), {
            throw IOException("Network or unrelated storage failure")
        }, {}, preserveCorruptLocalData = { recoveries++ })
        runCurrent()
        assertFalse(startup.localRecoveryAvailable.value)
        startup.recoverLocalData()
        runCurrent()
        assertEquals(0, recoveries)
        assertEquals(StartupState.Failed, startup.state.value)
    }

    @Test fun failedPreservationDoesNotReloadOrStartPlaybackAndRemainsRetryable() = runTest {
        var loads = 0
        var playbackStarts = 0
        val startup = StudioStartup(backgroundScope, StandardTestDispatcher(testScheduler), {
            loads++
            throw LegacyStudioStateCorruptedException(IOException("fixture"))
        }, { playbackStarts++ }, preserveCorruptLocalData = { throw IOException("Cannot preserve") })
        runCurrent()
        startup.recoverLocalData()
        runCurrent()
        assertEquals(1, loads)
        assertEquals(0, playbackStarts)
        assertEquals(StartupState.Failed, startup.state.value)
        assertTrue(startup.localRecoveryAvailable.value)
    }

    @Test fun playbackAndReadyWaitForRestoredAccountAndConcurrentRetryIsIgnored() = runTest {
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val startup = StudioStartup(backgroundScope, StandardTestDispatcher(testScheduler), {
            events += "data"
            release.await()
            events += "restored"
        }, { events += "playback" })
        runCurrent()
        assertEquals(StartupState.Loading, startup.state.value)
        assertEquals(listOf("data"), events)
        startup.retry()
        runCurrent()
        assertEquals(listOf("data"), events)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("data", "restored", "playback"), events)
        assertEquals(StartupState.Ready, startup.state.value)
        startup.retry()
        runCurrent()
        assertEquals(3, events.size)
    }

    @Test fun failedDataLoadCanRetryWithoutStartingPlaybackEarly() = runTest {
        var attempts = 0
        var playbackStarts = 0
        val startup = StudioStartup(backgroundScope, StandardTestDispatcher(testScheduler), {
            attempts++
            if (attempts == 1) throw IOException("test")
        }, { playbackStarts++ })
        runCurrent()
        assertEquals(StartupState.Failed, startup.state.value)
        assertEquals(0, playbackStarts)
        startup.retry()
        runCurrent()
        assertEquals(StartupState.Ready, startup.state.value)
        assertEquals(2, attempts)
        assertEquals(1, playbackStarts)
    }

    @Test fun cancellationNeverStartsPlaybackOrPublishesReady() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        var starts = 0
        var failures = 0
        val startup =
            StudioStartup(scope, StandardTestDispatcher(testScheduler), {
                release.await()
            }, { starts++ }, { failures++ })
        runCurrent()
        scope.cancel()
        release.complete(Unit)
        runCurrent()
        assertEquals(StartupState.Loading, startup.state.value)
        assertEquals(0, starts)
        assertEquals(0, failures)
    }
}
