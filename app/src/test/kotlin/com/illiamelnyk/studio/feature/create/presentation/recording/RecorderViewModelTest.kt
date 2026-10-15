package com.illiamelnyk.studio.feature.create.presentation.recording

import com.illiamelnyk.studio.feature.create.domain.AudioRecorder
import com.illiamelnyk.studio.feature.create.domain.RecordedAudio
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class RecorderViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun permissionDenialNeverOpensMicrophone() = runTest(main.testDispatcher) {
        val recorder = TestRecorder()
        val subject = RecorderViewModel(recorder)
        subject.onIntent(RecorderIntent.PermissionDenied)
        assertEquals(0, recorder.starts)
        assertNotNull(subject.state.value.error)
    }

    @Test
    fun recordingPausesResumesAndProducesReference() = runTest(main.testDispatcher) {
        val recorder = TestRecorder()
        val subject = RecorderViewModel(recorder)
        subject.onIntent(RecorderIntent.Start)
        runCurrent()
        subject.onIntent(RecorderIntent.Pause)
        assertEquals(true, subject.state.value.paused)
        subject.onIntent(RecorderIntent.Resume)
        assertEquals(false, subject.state.value.paused)
        subject.onIntent(RecorderIntent.Finish)
        assertEquals("file:///idea.m4a", subject.state.value.audio?.uri)
        assertFalse(subject.state.value.recording)
        assertEquals(1, recorder.starts)
    }

    @Test
    fun failedImportShowsErrorWithoutReference() = runTest(main.testDispatcher) {
        val subject = RecorderViewModel(TestRecorder())
        subject.onIntent(RecorderIntent.Import("broken"))
        runCurrent()
        assertNotNull(subject.state.value.error)
        assertEquals(null, subject.state.value.audio)
        assertFalse(subject.state.value.importing)
    }

    private class TestRecorder : AudioRecorder {
        var starts = 0
        override fun start() {
            starts++
        }
        override fun pause() = Unit
        override fun resume() = Unit
        override fun elapsedMs() = 12_000L
        override fun amplitude() = .4f
        override fun release() = Unit
        override fun finish() = RecordedAudio("file:///idea.m4a", "Idea", 12_000)
        override suspend fun importAudio(uri: String): RecordedAudio = error("Invalid audio")
    }
}
