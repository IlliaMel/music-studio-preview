package com.illiamelnyk.studio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.navigation.AppRoot
import com.illiamelnyk.studio.navigation.AppViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** The only activity: every screen is a Compose destination inside it. */
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by appViewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (state.theme) {
                ThemePreference.Light -> false

                ThemePreference.Dark -> true

                // Until the stored choice is read, the system setting, which the window already follows.
                ThemePreference.System, null -> isSystemInDarkTheme()
            }
            // The scheme can differ from the system setting, so the bar icons follow the scheme:
            // dark icons over the light one, light icons over the dark one.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            StudioTheme(darkTheme) {
                AppRoot(state)
            }
        }
    }
}
