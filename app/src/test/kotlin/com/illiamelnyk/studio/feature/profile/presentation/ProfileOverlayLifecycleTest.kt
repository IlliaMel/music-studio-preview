package com.illiamelnyk.studio.feature.profile.presentation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTextReplacement
import com.illiamelnyk.studio.core.data.fake.FakeCreditsRepository
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.data.fake.FakeTrackRepository
import com.illiamelnyk.studio.core.domain.session.DeleteAccount
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.SignOut
import com.illiamelnyk.studio.core.presentation.ToastController
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.testing.InMemoryThemePreferenceRepository
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileOverlayLifecycleTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After fun close() {
        stopKoin()
        scope.cancel()
    }

    @Test
    fun editingSurvivesActivityRecreationAndDismissalDiscardsPrivateDraft() {
        val sessions = FakeSessionRepository().apply {
            signIn("profile-owner", SignInMethod.Google, "Original profile", useProviderProfile = true)
        }
        val credits = FakeCreditsRepository()
        val playback = FakePlaybackController(scope)
        val tracks = FakeTrackRepository(credits, sessions, scope, Clock.System)
        val themes = InMemoryThemePreferenceRepository()
        val feedback = ToastController(scope)
        val created = mutableListOf<ProfileViewModel>()
        startKoin {
            modules(
                module {
                    single { feedback }
                    factory {
                        ProfileViewModel(
                            sessions,
                            credits,
                            themes,
                            SignOut(sessions, playback),
                            DeleteAccount(sessions, playback, tracks),
                            feedback,
                        ).also { created += it }
                    }
                },
            )
        }
        val shown = mutableStateOf(true)
        val content: @Composable () -> Unit = {
            StudioTheme {
                if (shown.value) ProfileOverlay(onClose = { shown.value = false })
            }
        }
        composeRule.activityRule.scenario.onActivity { it.setContent(content = content) }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Private unsaved profile")
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { it.setContent(content = content) }
        composeRule.onNode(hasSetTextAction()).assertTextContains("Private unsaved profile")
        composeRule.runOnIdle {
            assertEquals(1, created.size)
            shown.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { shown.value = true }
        composeRule.onNode(hasSetTextAction()).assertTextContains("Original profile")
        composeRule.runOnIdle { assertEquals(2, created.size) }
    }
}
