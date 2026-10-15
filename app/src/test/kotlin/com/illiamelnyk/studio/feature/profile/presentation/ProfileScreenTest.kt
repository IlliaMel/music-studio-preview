package com.illiamelnyk.studio.feature.profile.presentation

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `editing the name and Save dispatch separate intents`() {
        var intent: ProfileIntent? = null
        compose.setContent {
            StudioTheme {
                ProfileScreen(
                    ProfileState(user = ProfileUserUi("Illia", null, "illia"), name = "Illia"),
                    onIntent = { intent = it },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("New name")
        compose.waitUntil { intent == ProfileIntent.NameChanged("New name") }
        assertEquals(ProfileIntent.NameChanged("New name"), intent)
        compose.onNodeWithText("Save").performClick()
        assertEquals(ProfileIntent.SaveClicked, intent)
    }
}
