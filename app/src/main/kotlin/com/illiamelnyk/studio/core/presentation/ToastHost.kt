package com.illiamelnyk.studio.core.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.ui.components.StudioToast
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.koin.compose.koinInject

/** Each window registers a host; the top window owns the Popup without restarting its timer. */
@Composable
fun ToastHost(priority: Int = 0, controller: ToastController = koinInject()) {
    var hostId by remember(controller, priority) { mutableStateOf<Long?>(null) }
    DisposableEffect(controller, priority) {
        val id = controller.registerHost(priority)
        hostId = id
        onDispose { controller.unregisterHost(id) }
    }
    val activeHost by controller.activeHost.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    if (hostId == null || activeHost != hostId) return
    val visible = message ?: return
    Popup(popupPositionProvider = WindowTopPosition, properties = PopupProperties(focusable = false)) {
        StudioToast(
            title = visible.title.asString(),
            message = visible.detail?.asString().orEmpty(),
            modifier = Modifier.statusBarsPadding().padding(StudioTheme.dimens.layout.screenPadding),
            kind = if (visible.isError) StudioToastKind.Error else StudioToastKind.Info,
        )
    }
}

/** Position relative to the display window, including when hosted by a compact dialog/menu. */
private object WindowTopPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset((windowSize.width - popupContentSize.width) / 2, 0)
}
