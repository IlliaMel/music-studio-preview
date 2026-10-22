package com.illiamelnyk.studio

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationTarget
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationTargets
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.AppFeedbackHost
import com.illiamelnyk.studio.core.presentation.ToastHost
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioConfirmDialog
import com.illiamelnyk.studio.core.ui.foundation.LocalWindowFeedback
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.WindowFeedbackRenderer
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.navigation.AppRoot
import com.illiamelnyk.studio.navigation.AppState
import com.illiamelnyk.studio.navigation.AppViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** The only activity: every screen is a Compose destination inside it. */
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModel()
    private val notificationTargets: GenerationNotificationTargets by inject()
    private var firstContentReady = false
    private var startupPreDraw: ViewTreeObserver.OnPreDrawListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptGenerationIntent(intent)
        enableEdgeToEdge()
        val decor = window.decorView
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstContentReady) return false
                decor.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
                startupPreDraw = null
                if (BuildConfig.DEBUG) {
                    val elapsed = SystemClock.elapsedRealtime() - (application as StudioApp).startupStartedAt
                    android.util.Log.d("StudioStartup", "firstContentPreDrawMs=$elapsed")
                }
                return true
            }
        }
        startupPreDraw = listener
        decor.viewTreeObserver.addOnPreDrawListener(listener)
        setContent {
            val startup = (application as StudioApp).startup
            val startupState by startup.state.collectAsStateWithLifecycle()
            val localRecoveryAvailable by startup.localRecoveryAvailable.collectAsStateWithLifecycle()
            var confirmLocalRecovery by remember(startupState) { mutableStateOf(false) }
            val state = if (startupState == StartupState.Ready) {
                appViewModel.state.collectAsStateWithLifecycle().value
            } else {
                AppState()
            }
            SideEffect {
                if (startupState == StartupState.Failed || state.readyForFirstFrame) firstContentReady = true
            }
            ReportDrawnWhen { state.readyForFirstFrame || startupState == StartupState.Failed }
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
                CompositionLocalProvider(LocalWindowFeedback provides AppWindowFeedback) {
                    AppFeedbackHost {
                        if (startupState == StartupState.Failed) {
                            Column(
                                Modifier.fillMaxSize().background(StudioTheme.colors.background),
                                verticalArrangement = Arrangement.spacedBy(
                                    StudioTheme.dimens.spacing.m,
                                    Alignment.CenterVertically,
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                StudioText(
                                    stringResource(
                                        if (localRecoveryAvailable) {
                                            R.string.startup_local_state_unreadable
                                        } else {
                                            R.string.error_unknown
                                        },
                                    ),
                                    StudioTheme.typography.bodyMedium,
                                )
                                StudioButton(stringResource(R.string.ui_retry), startup::retry)
                                if (localRecoveryAvailable) {
                                    StudioButton(stringResource(R.string.startup_local_recovery_action), {
                                        confirmLocalRecovery =
                                            true
                                    })
                                }
                            }
                            if (confirmLocalRecovery && localRecoveryAvailable) {
                                StudioConfirmDialog(
                                    title = stringResource(R.string.startup_local_recovery_title),
                                    message = stringResource(R.string.startup_local_recovery_message),
                                    confirmLabel = stringResource(R.string.startup_local_recovery_confirm),
                                    dismissLabel = stringResource(R.string.profile_cancel),
                                    onConfirm = {
                                        confirmLocalRecovery = false
                                        startup.recoverLocalData()
                                    },
                                    onDismissRequest = { confirmLocalRecovery = false },
                                )
                            }
                        } else {
                            AppRoot(state)
                        }
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        (application as StudioApp).recordForeground(true)
    }

    override fun onStop() {
        (application as StudioApp).recordForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        startupPreDraw?.let { listener ->
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
        }
        startupPreDraw = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptGenerationIntent(intent)
    }

    private fun acceptGenerationIntent(intent: Intent) {
        if (intent.action != GenerationNotificationTarget.ACTION) return
        val owner = intent.getStringExtra(GenerationNotificationTarget.OWNER)
        val track = intent.getStringExtra(GenerationNotificationTarget.TRACK)
        if (!owner.isNullOrBlank() && !track.isNullOrBlank()) {
            notificationTargets.request(GenerationNotificationTarget(owner, TrackId(track)))
        }
        intent.action = null
        intent.removeExtra(GenerationNotificationTarget.OWNER)
        intent.removeExtra(GenerationNotificationTarget.TRACK)
    }
}

private object AppWindowFeedback : WindowFeedbackRenderer {
    @Composable
    override fun Render(depth: Int) {
        ToastHost(priority = depth)
    }
}
