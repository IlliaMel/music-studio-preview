package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.illiamelnyk.studio.core.ui.foundation.FeedbackWindow
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A [StudioBottomSheet] in a window of its own, over the whole app.
 *
 * Screens are drawn under the shell's bars and mini player, so a sheet laid out inside a screen
 * would slide under them. A dialog window covers them edge to edge instead, without a dim or an
 * animation of its own: the sheet keeps its scrim, its drag and its back gesture. The window opens
 * with the sheet and closes once the sheet has left, so it never blocks touches on its own.
 */
@Composable
fun OverlaySheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Whether the sheet's content is composed: from the moment it opens until it has left.
    var onScreen by remember { mutableStateOf(false) }
    if (!visible && !onScreen) return
    Dialog(
        onDismissRequest = onDismissRequest,
        // The sheet handles the back gesture and taps on its scrim itself.
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        FeedbackWindow {
            PlainWindow()
            StudioBottomSheet(
                visible = visible,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                showCloseButton = showCloseButton,
            ) {
                DisposableEffect(Unit) {
                    onScreen = true
                    onDispose { onScreen = false }
                }
                content()
            }
        }
    }
}

/**
 * Makes the dialog's window a plain surface for the sheet: no dim and no window animation, and
 * system bar icons that read over the scheme, like the activity's.
 */
@Composable
private fun PlainWindow() {
    val view = LocalView.current
    val lightScheme = StudioTheme.colors.background.luminance() > HALF
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setDimAmount(0f)
        window.setWindowAnimations(0)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightScheme
            isAppearanceLightNavigationBars = lightScheme
        }
    }
}

private const val HALF = 0.5f
