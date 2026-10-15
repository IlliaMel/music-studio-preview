package com.illiamelnyk.studio.feature.auth.presentation.welcome

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `welcome offers Google and demo without extra onboarding choices`() {
        var intent: WelcomeIntent? = null
        compose.setContent { StudioTheme { WelcomeScreen(WelcomeState(), onIntent = { intent = it }) } }
        compose.onNodeWithText("Continue with local server").performClick()
        assertEquals(WelcomeIntent.ContinueWithGoogleClicked, intent)
        compose.onNodeWithText("Try the demo").performClick()
        assertEquals(WelcomeIntent.TryDemoClicked, intent)
        compose.onNodeWithText("18+").assertDoesNotExist()
    }
}
