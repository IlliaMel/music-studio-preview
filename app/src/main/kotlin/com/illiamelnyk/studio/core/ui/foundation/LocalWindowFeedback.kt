package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** App-injected renderer; the UI kit knows only the nesting depth of the window. */
interface WindowFeedbackRenderer {
    @Composable
    fun Render(depth: Int)
}

private object NoWindowFeedback : WindowFeedbackRenderer {
    @Composable
    override fun Render(depth: Int) = Unit
}

val LocalWindowFeedback = staticCompositionLocalOf<WindowFeedbackRenderer> { NoWindowFeedback }
val LocalFeedbackWindowDepth = staticCompositionLocalOf { 0 }

/** Wrap each Dialog's contents so nested windows take feedback ownership even after restoration. */
@Composable
fun FeedbackWindow(content: @Composable () -> Unit) {
    val depth = LocalFeedbackWindowDepth.current + 1
    CompositionLocalProvider(LocalFeedbackWindowDepth provides depth) {
        content()
        LocalWindowFeedback.current.Render(depth)
    }
}
