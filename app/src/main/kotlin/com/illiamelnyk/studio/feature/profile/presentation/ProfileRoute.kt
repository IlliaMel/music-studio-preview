package com.illiamelnyk.studio.feature.profile.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.illiamelnyk.studio.core.presentation.ObserveEffects
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.foundation.FeedbackWindow
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * The profile, wired to its ViewModel.
 *
 * @param onClose leaves the screen.
 * @param contentPadding the space the system bars and the mini player take over the content.
 */
@Composable
fun ProfileRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(true) }
    val close by rememberUpdatedState(onClose)
    val exitDuration = StudioTheme.motion.duration.long
    LaunchedEffect(visible) {
        if (!visible) {
            delay(exitDuration.toLong())
            close()
        }
    }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.Close -> visible = false
        }
    }
    Dialog(
        onDismissRequest = { viewModel.onIntent(ProfileIntent.BackClicked) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FeedbackWindow {
            val view = LocalView.current
            val light = StudioTheme.colors.background.luminance() > 0.5f
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window
                window?.setDimAmount(0f)
                if (window != null) {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
            }
            StudioBottomSheet(visible = visible, onDismissRequest = { viewModel.onIntent(ProfileIntent.BackClicked) }) {
                ProfileScreen(
                    state = state,
                    onIntent = viewModel::onIntent,
                    modifier = modifier,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/** The sheet owns a fresh ViewModel for each opening, so dismissed edits never leak into the next one. */
@Composable
fun ProfileOverlay(onClose: () -> Unit) {
    val owner = rememberViewModelStoreOwner()
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        ProfileRoute(onClose = onClose)
    }
}
