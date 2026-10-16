package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerDraftStoreTest {
    @Test
    fun sourcesKeepSelectionOrderWithoutDuplicatesAndCanBeRemovedIndividually() {
        val store = ComposerDraftStore { "test-owner" }
        val first = track("first", 30.seconds)
        val second = track("second", 30.seconds)
        store.remix(first)
        store.remix(second)
        store.remix(first)
        assertEquals(listOf(first, second), store.sources.value)
        store.remove(first.id)
        assertEquals(listOf(second), store.sources.value)
        store.clear()
        assertEquals(emptyList<Track>(), store.sources.value)
        assertNull(store.pending.value)
    }

    @Test
    fun fifthSourceIsRejectedWithAnExplicitProblemWithoutDroppingEarlierSources() {
        val store = ComposerDraftStore { "test-owner" }
        val originals = (1..5).map { track("original-" + it, 30.seconds) }
        originals.forEach(store::remix)
        assertEquals(originals.take(4), store.sources.value)
        assertEquals(ComposerDraftStore.Problem.TooMany, store.problem.value)
    }

    @Test
    fun remixedAndNotReadyTracksCannotBecomeSources() {
        val store = ComposerDraftStore { "test-owner" }
        store.remix(track("remix", 30.seconds).copy(remixTrackIds = listOf("original")))
        assertEquals(ComposerDraftStore.Problem.AlreadyRemixed, store.problem.value)
        store.remix(track("queued", 30.seconds).copy(status = TrackStatus.Queued))
        assertEquals(ComposerDraftStore.Problem.NotReady, store.problem.value)
        assertEquals(emptyList<Track>(), store.sources.value)
    }

    @Test
    fun cachedFlowGettersAndNewCollectorsCannotExposeThePreviousOwnersSources() = runTest {
        var owner: String? = "owner-a"
        val store = ComposerDraftStore { owner }
        val sources = store.sources
        val pending = store.pending
        store.remix(track("private-a", 30.seconds))
        owner = null
        assertEquals(emptyList<Track>(), sources.value)
        assertNull(pending.value)
        owner = "owner-b"
        assertEquals(listOf(emptyList<Track>()), sources.replayCache)
        val seen = mutableListOf<List<Track>>()
        backgroundScope.launch { sources.collect { seen += it } }
        runCurrent()
        assertEquals(listOf(emptyList<Track>()), seen)
    }

    @Test
    fun automaticOwnerRefreshClearsAnAlreadyActiveCollectorAndAllowsOnlyTheNewOwnerSources() = runTest {
        var owner: String? = "owner-a"
        val store = ComposerDraftStore { owner }
        val seen = mutableListOf<List<Track>>()
        backgroundScope.launch { store.sources.collect { seen += it } }
        runCurrent()
        store.remix(track("private-a", 30.seconds))
        runCurrent()
        owner = null
        store.refreshOwner()
        runCurrent()
        assertEquals(emptyList<Track>(), seen.last())
        owner = "owner-b"
        val next = track("private-b", 30.seconds)
        store.remix(next)
        runCurrent()
        assertEquals(listOf(next), seen.last())
    }

    @Test
    fun missingProductionOwnerFailsClosedWithoutAcceptingSources() {
        val store = ComposerDraftStore()
        store.remix(track("private", 30.seconds))
        assertEquals(emptyList<Track>(), store.sources.value)
        assertNull(store.pending.value)
        assertEquals(0, store.revision.value)
    }

    @Test
    fun rawRecordingIsRejectedAsARemixSourceButAnOrdinaryOriginalIsAccepted() {
        val store = ComposerDraftStore { "owner" }
        val recording = track("recording-8ced07a1-2964-4a3f-aa80-75177020463b", 30.seconds)
        val original = track("original-track", 30.seconds)
        assertEquals(false, recording.canRemixOriginal)
        assertEquals(true, original.canRemixOriginal)
        store.remix(recording)
        assertEquals(emptyList<Track>(), store.sources.value)
        assertEquals(ComposerDraftStore.Problem.RecordingReference, store.problem.value)
        store.remix(original)
        assertEquals(listOf(original), store.sources.value)
        assertNull(store.problem.value)
    }
}
