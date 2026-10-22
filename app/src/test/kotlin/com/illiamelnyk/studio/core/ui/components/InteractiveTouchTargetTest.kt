package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioDimens
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The components added after the first set: every one can be touched within 48 dp and has its role. */
@RunWith(RobolectricTestRunner::class)
class InteractiveTouchTargetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val touchTarget = StudioDimens().size.touchTarget

    @Test
    fun `cards, tiles and rows have a 48 dp touch target and the button role`() {
        setThemedContent {
            TrackCard("Light Through Clouds", "DEREK", "Light Through Clouds", onClick = {})
            StationCard("Late Night Lo-fi", "Studio", "Late Night Lo-fi", onClick = {})
            ArtistAvatar("Lei Swan", "Lei Swan", onClick = {})
            SectionHeader("Trending Now", onTitleClick = {})
            ToolTile("Genreshift", "Genreshift", onClick = {})
            SettingRow("Default model", value = "Music v2.5", onClick = {})
            SoundRow("Rally Bassground", "808 Bass", "Rally Bassground", playing = false, onPlayClick = {})
        }

        listOf("Light Through Clouds", "Late Night Lo-fi", "Lei Swan", "Trending Now", "Genreshift", "Default model")
            .forEach { text -> composeRule.onNodeWithText(text).assertTouchTarget(Role.Button) }
        composeRule.onNodeWithContentDescription(string(R.string.ui_play_named, "Rally Bassground"))
            .assertTouchTarget(Role.Button)
    }

    @Test
    fun `inputs have a 48 dp touch target and their roles`() {
        setThemedContent {
            StudioSearchField("lo-fi", onQueryChange = {}, onSearch = {}, placeholder = "Search sounds")
            StudioCheckbox(checked = false, onCheckedChange = {}, label = "I want to receive updates.")
            DurationPicker(listOf(60, 90), selected = 60, onSelect = {}) { "$it s" }
            PromptTemplate(
                kind = "New track",
                name = "Genreshift",
                imageSeed = "Genreshift",
                parts = listOf(PromptPart.Text("Transform it into "), PromptPart.Slot("genre", "New genre")),
                onSlotClick = {},
            )
            Composer(value = "A slow beat", onValueChange = {}, onSend = {}, expanded = false, placeholder = "Describe")
        }

        composeRule.onNodeWithContentDescription(string(R.string.ui_clear)).assertTouchTarget(Role.Button)
        composeRule.onNodeWithText("I want to receive updates.").assertTouchTarget(Role.Checkbox)
        composeRule.onNodeWithText("60 s").assertTouchTarget(Role.Checkbox)
        composeRule.onNodeWithContentDescription("New genre").assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_send)).assertTouchTarget(Role.Button)
    }

    @Test
    fun `menu items have a 48 dp touch target and the button role`() {
        setThemedContent {
            Box {
                StudioContextMenu(expanded = true, onDismissRequest = {}) {
                    ContextMenuItem("Play next", onClick = {}, icon = StudioIcons.PlaylistPlay)
                    ContextMenuItem("Delete", onClick = {}, icon = StudioIcons.Delete, danger = true)
                }
            }
        }

        composeRule.onNodeWithText("Play next").assertTouchTarget(Role.Button)
        composeRule.onNodeWithText("Delete").assertTouchTarget(Role.Button)
    }

    @Test
    fun `player controls have a 48 dp touch target and their roles`() {
        setThemedContent {
            MiniPlayer(
                title = "Night Drive",
                subtitle = "Studio",
                coverSeed = "Night Drive",
                isPlaying = true,
                onClick = {},
                onPlayPause = {},
                onNext = {},
            )
            PlayerControls(
                isPlaying = false,
                onPlayPause = {},
                onPrevious = {},
                onNext = {},
                shuffle = true,
                onShuffleChange = {},
                repeat = false,
                onRepeatChange = {},
            )
            SeekBar(progress = { 0.5f }, onSeek = {}, elapsed = "01:00", remaining = "-01:00")
            Box(Modifier.height(LyricsHeight)) {
                LyricsView(
                    lines = listOf(LyricLine(0, listOf(LyricWord("Radio", 0), LyricWord("plays", 500)))),
                    positionMs = { 0 },
                    onSeek = {},
                )
            }
        }

        composeRule.onNodeWithText("Night Drive").assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_pause)).assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_play)).assertTouchTarget(Role.Button)
        composeRule.onAllNodesWithText("Radio plays")[0].assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_previous)).assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_shuffle)).assertTouchTarget(Role.Switch)
        composeRule.onNodeWithContentDescription(string(R.string.ui_repeat)).assertTouchTarget(Role.Switch)
        val seekBar = composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)))
        val bounds = seekBar.fetchSemanticsNode().boundsInRoot
        with(composeRule.density) { assertTrue("seek bar height", bounds.height.toDp() >= touchTarget) }
    }

    private fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                Column { content() }
            }
        }
    }

    private fun SemanticsNodeInteraction.assertTouchTarget(role: Role) {
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, role))
        val bounds = fetchSemanticsNode().touchBoundsInRoot
        with(composeRule.density) {
            assertTrue("touch width ${bounds.width.toDp()}", bounds.width.toDp() >= touchTarget)
            assertTrue("touch height ${bounds.height.toDp()}", bounds.height.toDp() >= touchTarget)
        }
    }

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private companion object {
        val LyricsHeight = StudioDimens().size.component.stationHeight
    }
}
