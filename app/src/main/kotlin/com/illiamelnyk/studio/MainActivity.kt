package com.illiamelnyk.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** The only activity: every screen is a Compose destination inside it. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // StudioTheme follows the system setting, and by default so do the system bar icons: dark
        // icons over the light scheme, light icons over the dark one.
        enableEdgeToEdge()
        setContent {
            StudioTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(StudioTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    StudioText(stringResource(R.string.app_name), StudioTheme.typography.headlineLarge)
                }
            }
        }
    }
}
