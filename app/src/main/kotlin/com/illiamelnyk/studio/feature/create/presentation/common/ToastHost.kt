package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioToast
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A toast a ViewModel asks for; its texts are resolved when it shows. */
@Immutable
data class ToastUi(val title: UiText, val message: UiText, val kind: StudioToastKind = StudioToastKind.Info)

/**
 * The toasts of a screen, one at a time: [show] waits for the toast on screen to leave, so toasts
 * that come together queue up instead of replacing each other.
 *
 * @param initial a toast on screen from the start, for previews; it stays until another one shows.
 */
@Stable
class ToastHostState(initial: ToastUi? = null) {
    /** The toast on screen, also while it leaves. */
    var current: ToastUi? by mutableStateOf(initial)
        private set

    /** Whether [current] is in place; it slides away when this turns `false`. */
    var visible: Boolean by mutableStateOf(initial != null)
        private set

    private val queue = Mutex()

    /** Shows [toast] for a few seconds, after the toasts before it; returns once it has left. */
    suspend fun show(toast: ToastUi) = queue.withLock {
        try {
            current = toast
            visible = true
            delay(if (toast.kind == StudioToastKind.Error) ERROR_TIME else INFO_TIME)
        } finally {
            visible = false
        }
        delay(LEAVE_TIME)
        current = null
    }

    private companion object {
        val INFO_TIME: Duration = 3.seconds
        val ERROR_TIME: Duration = 4.seconds

        /** Longer than the toast takes to slide away. */
        val LEAVE_TIME: Duration = 300.milliseconds
    }
}

/**
 * Where the toasts of [state] appear: at the top of the screen, sliding down into place and back
 * up when they leave. Place it over the content, below the top bar.
 */
@Composable
fun ToastHost(state: ToastHostState, modifier: Modifier = Modifier) {
    val motion = StudioTheme.motion
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StudioTheme.dimens.layout.screenPadding),
        enter = slideInVertically(tween(motion.duration.long, easing = motion.easing.emphasized)) { -it } +
            fadeIn(tween(motion.duration.medium, easing = motion.easing.standard)),
        exit = slideOutVertically(tween(motion.duration.medium, easing = motion.easing.standard)) { -it } +
            fadeOut(tween(motion.duration.medium, easing = motion.easing.standard)),
    ) {
        state.current?.let { toast ->
            StudioToast(title = toast.title.asString(), message = toast.message.asString(), kind = toast.kind)
        }
    }
}

@PreviewLightDark
@Composable
private fun ToastHostPreview() {
    PreviewScreen {
        val toast = ToastUi(UiText.Resource(R.string.create_toast_ready), UiText.Raw("Rainy window lo-fi · 2:00"))
        ToastHost(remember { ToastHostState(initial = toast) })
    }
}
