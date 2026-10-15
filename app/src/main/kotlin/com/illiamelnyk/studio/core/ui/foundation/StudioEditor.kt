package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

internal class EditorAcknowledgements(initial: String) {
    private var external = initial
    private val pending = ArrayDeque<String>()

    fun sent(text: String) {
        pending.addLast(text)
        if (pending.size > 128) pending.removeFirst()
    }

    fun isPreset(text: String): Boolean {
        if (text == external) return false
        external = text
        val index = pending.indexOf(text)
        if (index >= 0) {
            repeat(index + 1) { pending.removeFirst() }
            return false
        }
        pending.clear()
        return true
    }
}

@Composable
internal fun rememberStudioEditor(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val editor = rememberTextFieldState(value)
    val acknowledgements = remember { EditorAcknowledgements(value) }
    val send by rememberUpdatedState(onValueChange)
    LaunchedEffect(editor) {
        var observed = value
        snapshotFlow { editor.text.toString() }.collect { text ->
            if (text != observed) {
                observed = text
                acknowledgements.sent(text)
                send(text)
            }
        }
    }
    SideEffect {
        if (acknowledgements.isPreset(value)) editor.setTextAndPlaceCursorAtEnd(value)
    }
    return editor
}
