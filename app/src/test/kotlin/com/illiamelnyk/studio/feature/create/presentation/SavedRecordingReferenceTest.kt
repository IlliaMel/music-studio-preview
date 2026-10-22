package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.RecordingReference
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.NotificationChoice
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedRecordingReferenceTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun savedRecordingMenuSelectsReferenceAndReplacesRemixWithoutSubmittingOrSpending() = runTest(main.testDispatcher) {
        val fixture = Fixture(this)
        fixture.drafts.remix(fixture.original)
        runCurrent()
        val credits = fixture.credits.credits.first()
        fixture.select()
        runCurrent()
        assertEquals("Saved idea", fixture.vm.state.value.composer.referenceLabel)
        assertTrue(fixture.vm.state.value.composer.remixSources.isEmpty())
        assertTrue(fixture.drafts.sources.value.isEmpty())
        assertEquals(credits, fixture.credits.credits.first())
        assertNull(fixture.request)
        fixture.vm.onIntent(CreateIntent.PromptChanged("Build a song around this recorded idea"))
        fixture.vm.onIntent(CreateIntent.SendClicked)
        runCurrent()
        assertEquals(fixture.recording.audioUri, fixture.request!!.referenceAudioUri)
        assertTrue(fixture.request!!.remixTrackIds.isEmpty())
    }

    @Test
    fun lateSelectionCannotExposeOldOwnersReferenceOrReplaceANewerImportedReference() = runTest(main.testDispatcher) {
        val fixture = Fixture(this)
        fixture.gate = CompletableDeferred()
        fixture.select()
        runCurrent()
        fixture.owner = "new-owner"
        fixture.gate!!.complete(Unit)
        runCurrent()
        assertNull(fixture.vm.state.value.composer.referenceLabel)
        assertNull(fixture.request)

        val second = Fixture(this)
        second.gate = CompletableDeferred()
        second.select()
        runCurrent()
        second.vm.onIntent(CreateIntent.ReferenceSelected("content://fresh-reference", "Fresh import"))
        second.gate!!.complete(Unit)
        runCurrent()
        assertEquals("Fresh import", second.vm.state.value.composer.referenceLabel)
        assertNull(second.request)
    }

    @Test
    fun deletedRecordingOrLaterRemixSelectionCannotCompletePendingReferenceSelection() = runTest(main.testDispatcher) {
        val fixture = Fixture(this)
        fixture.gate = CompletableDeferred()
        fixture.select()
        runCurrent()
        fixture.mine.value = emptyList()
        fixture.gate!!.complete(Unit)
        runCurrent()
        assertNull(fixture.vm.state.value.composer.referenceLabel)

        val second = Fixture(this)
        second.gate = CompletableDeferred()
        second.select()
        runCurrent()
        second.drafts.remix(second.original)
        runCurrent()
        second.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(second.original.id), second.vm.state.value.composer.remixSources.map { it.id })
        assertNull(second.request)
    }

    @Test
    fun onlyReadySavedRecordingsExposeTheReferenceActionAndOrdinarySongInvocationIsIgnored() =
        runTest(main.testDispatcher) {
            val fixture = Fixture(this)
            assertTrue(fixture.recording.toCreateUi().canUseAsReference)
            assertFalse(fixture.original.toCreateUi().canUseAsReference)
            assertFalse(fixture.recording.copy(status = TrackStatus.Queued).toCreateUi().canUseAsReference)
            fixture.vm.onIntent(CreateIntent.MoreClicked(fixture.original.id))
            fixture.vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.UseAsReference))
            runCurrent()
            assertEquals(0, fixture.preparations)
            assertNull(fixture.vm.state.value.composer.referenceLabel)
        }

    private class Fixture(scope: TestScope) {
        var owner = "owner"
        val credits = FakeCreditsRepository()
        val drafts = ComposerDraftStore { owner }
        val original = DemoCatalog.tracks.first().copy(status = TrackStatus.Ready)
        val recording = original.copy(
            id = TrackId("recording-8ced07a1-2964-4a3f-aa80-75177020463b"),
            title = "Saved idea",
            audioUri = "http://10.0.2.2:8787/v1/recordings/8ced07a1-2964-4a3f-aa80-75177020463b/audio",
        )
        val mine = MutableStateFlow(listOf(original, recording))
        var gate: CompletableDeferred<Unit>? = null
        var request: GenerationRequest? = null
        var preparations = 0
        private val playback = FakePlaybackController(scope.backgroundScope)
        private val sessions = FakeSessionRepository().apply {
            signIn("test-owner", SignInMethod.Google, "Test listener", useProviderProfile = true)
        }
        private val tracks = object : TrackRepository by FakeTrackRepository(
            credits,
            sessions,
            scope.backgroundScope,
            Clock.System,
        ) {
            override fun observeMine() = mine
            override suspend fun prepareRecordingReference(id: TrackId): Outcome<RecordingReference> {
                preparations++
                val selectedOwner = owner
                gate?.await()
                return Outcome.Success(RecordingReference(id, selectedOwner, recording.audioUri!!, recording.title))
            }
            override fun isRecordingReferenceCurrent(reference: RecordingReference) =
                owner == reference.ownerId && mine.value.any { it.id == reference.id && it.canUseAsReference }

            override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> {
                this@Fixture.request = request
                return Outcome.Success(listOf(original))
            }
        }
        val vm = CreateViewModel(
            tracks,
            credits,
            playback,
            GenerateTracks(tracks, credits),
            PlayNext(playback),
            Clock.System,
            object : NotificationChoice {
                override suspend fun hasAnswered() = true
                override suspend fun isEnabled() = false
                override suspend fun answer(enabled: Boolean) = Unit
            },
            sessions,
            TestGenerationOptionsRepository(),
            drafts,
        )

        init {
            scope.runCurrent()
        }

        fun select() {
            vm.onIntent(CreateIntent.MoreClicked(recording.id))
            vm.onIntent(CreateIntent.MenuActionClicked(TrackMenuAction.UseAsReference))
        }
    }
}
