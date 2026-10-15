package com.illiamelnyk.studio.core.ui.foundation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAcknowledgementsTest {
    @Test
    fun staleEchoDoesNotReplaceNewerEditorBuffer() {
        val subject = EditorAcknowledgements("")
        subject.sent("Morning")
        subject.sent("Morning light")
        assertFalse(subject.isPreset("Morning"))
        assertFalse(subject.isPreset("Morning light"))
        assertTrue(subject.isPreset(""))
    }

    @Test
    fun presetsAndClampedValuesReplaceTheEditor() {
        val subject = EditorAcknowledgements("")
        subject.sent("Old draft")
        assertTrue(subject.isPreset("Use this prompt"))
        subject.sent("Too long")
        assertTrue(subject.isPreset("Too"))
    }
}
