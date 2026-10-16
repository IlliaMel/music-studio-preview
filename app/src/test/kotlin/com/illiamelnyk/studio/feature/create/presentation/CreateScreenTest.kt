package com.illiamelnyk.studio.feature.create.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Tool
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CreateScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val intents = mutableListOf<CreateIntent>()
    private var state by mutableStateOf(CreateState(tracks = CreatePreviewData.tracks, isLoading = false))

    @Test
    fun remixSourcesShowWithoutPromptOrFocusAndRemovingLastSourceCollapsesComposer() {
        val first = RemixSourceUi(TrackId("first-original"), "First original")
        val second = RemixSourceUi(TrackId("second-original"), "Second original")
        state = state.copy(composer = ComposerUi(remixSources = persistentListOf(first, second)))
        show { intent ->
            if (intent is CreateIntent.RemixSourceRemoved) {
                state = state.copy(
                    composer = state.composer.copy(
                        remixSources = state.composer.remixSources.filterNot { it.id == intent.id }.toImmutableList(),
                    ),
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
        composeRule.onNodeWithText(first.title).assertIsDisplayed()
        composeRule.onNodeWithText(second.title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.ui_send)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.ui_remove, second.title),
        ).performTouchInput { click() }
        assertEquals(CreateIntent.RemixSourceRemoved(second.id), intents.last())
        composeRule.onNodeWithText(second.title).assertDoesNotExist()
        composeRule.onNodeWithText(first.title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.ui_remove, first.title),
        ).performTouchInput { click() }
        assertEquals(CreateIntent.RemixSourceRemoved(first.id), intents.last())
        composeRule.onNodeWithText(first.title).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.ui_send)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.create_composer_settings)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun audioReferenceShowsWithoutPromptOrFocusAndCanBeRemovedByPointer() {
        val title = "Saved audio reference"
        state = state.copy(composer = ComposerUi(referenceLabel = title))
        show { intent ->
            if (intent == CreateIntent.ReferenceRemoved) {
                state = state.copy(composer = state.composer.copy(referenceLabel = null))
            }
        }
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.ui_send)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.ui_remove, title),
        ).performTouchInput { click() }
        assertEquals(CreateIntent.ReferenceRemoved, intents.last())
        composeRule.onNodeWithText(title).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.ui_send)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.create_composer_settings)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `typing reports the prompt, and send sends it once it can`() {
        show()
        val send = composeRule.onNodeWithContentDescription(string(R.string.ui_send))
        send.assertDoesNotExist()

        composeRule.onNode(hasSetTextAction()).performTextInput("Lo-fi beat")
        composeRule.waitUntil { intents.lastOrNull() == CreateIntent.PromptChanged("Lo-fi beat") }
        assertEquals(CreateIntent.PromptChanged("Lo-fi beat"), intents.last())

        state = state.copy(composer = ComposerUi(prompt = "Lo-fi beat", canSend = true))
        send.assertIsEnabled().performClick()

        assertEquals(CreateIntent.SendClicked, intents.last())
    }

    @Test
    fun `a composing track shows how far it is, as its percentage says`() {
        show()

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))).assertExists()
    }

    @Test
    fun `a tool's tile opens the tool`() {
        show()

        composeRule.onNodeWithText(string(R.string.tool_genreshift)).performClick()

        assertEquals(CreateIntent.ToolClicked(Tool.Genreshift), intents.last())
    }

    @Test
    fun `a refused prompt offers its suggestion, and a track's menu offers delete`() {
        state = state.copy(menuTrackId = TrackId("2"))
        show()

        // Failed rows have a Delete of their own; this one is in the menu's popup.
        composeRule.onNode(hasText(string(R.string.create_menu_delete)) and hasAnyAncestor(isPopup())).performClick()
        assertEquals(CreateIntent.MenuActionClicked(TrackMenuAction.Delete), intents.last())

        val useSuggestion = string(R.string.create_use_suggestion)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(useSuggestion))
        composeRule.onNodeWithText(useSuggestion).performClick()
        assertEquals(CreateIntent.FixClicked(TrackId("3")), intents.last())
    }

    @Test
    fun `an empty list offers ideas that fill the composer`() {
        state = state.copy(tracks = persistentListOf())
        show()
        val prompt = string(R.string.create_idea_rain_prompt)

        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(prompt))
        composeRule.onNodeWithText(prompt).performClick()

        assertEquals(CreateIntent.IdeaClicked(prompt), intents.last())
    }

    @Test
    fun `settings select Custom lyrics while Styles remains coming soon`() {
        state = state.copy(
            sheet = CreateSheet.Settings(
                SettingsUi(com.illiamelnyk.studio.core.domain.track.MusicModel.V2_5, duration = null),
            ),
        )
        show()
        composeRule.onNodeWithText(string(R.string.create_lyrics_custom)).performScrollTo().performClick()
        assertEquals(
            CreateIntent.SettingsCustomLyricsSelected,
            intents.last(),
        )
        composeRule.onNode(hasText(string(R.string.create_chip_styles)) and hasAnyAncestor(isDialog()))
            .performScrollTo().performClick()
        assertEquals(
            CreateIntent.ComingSoonClicked(
                com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature.Styles,
            ),
            intents.last(),
        )
    }

    @Test
    fun `collapsed composer keeps attach field and settings on one line then retains focus when expanded`() {
        show()
        val field = composeRule.onNode(hasSetTextAction())
        val fieldBounds = field.fetchSemanticsNode().boundsInRoot
        val attachBounds = composeRule.onNodeWithContentDescription(string(R.string.create_composer_upload))
            .fetchSemanticsNode().boundsInRoot
        val settingsBounds = composeRule.onNodeWithContentDescription(string(R.string.create_composer_settings))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(attachBounds.center.y - fieldBounds.center.y) < attachBounds.height)
        assertTrue(kotlin.math.abs(settingsBounds.center.y - fieldBounds.center.y) < settingsBounds.height)
        assertTrue(settingsBounds.left >= fieldBounds.right)
        field.performTextInput("Keep focus")
        field.assertIsFocused()
        composeRule.onNodeWithContentDescription(string(R.string.create_composer_lyrics)).assertExists()
    }

    private fun show(onIntent: (CreateIntent) -> Unit = {}) {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                CreateScreen(state = state, onIntent = {
                    intents += it
                    onIntent(it)
                })
            }
        }
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)
}
