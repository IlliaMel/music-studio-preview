package com.illiamelnyk.studio.feature.player.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PlayerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val track = NowPlayingUi(TrackId("a"), "Glass Harbor", "Mira Vale", "Glass Harbor")
    private val queue = persistentListOf(queued("b", "Paper Moons"))
    private val intents = mutableListOf<PlayerIntent>()

    @Test
    fun `the player's buttons say what the user did`() {
        show(
            PlayerContent(
                track = track,
                isPlaying = true,
                upNext = queue,
                lyrics = LyricsUi.Synced(persistentListOf()),
            ),
        )

        composeRule.onNodeWithText(string(R.string.player_like)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.player_lyrics)).assertIsOff().performClick()
        composeRule.onNodeWithContentDescription(string(R.string.ui_pause)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.player_queue)).performClick()
        composeRule.onNodeWithText(string(R.string.player_remix)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.player_collapse)).performClick()

        assertEquals(
            listOf(
                PlayerIntent.LikeClicked,
                PlayerIntent.LyricsClicked,
                PlayerIntent.PlayPauseClicked,
                PlayerIntent.QueueClicked,
                PlayerIntent.ComingSoonClicked(SoonFeature.Remix),
                PlayerIntent.CollapseClicked,
            ),
            intents,
        )
    }

    @Test
    fun `a station has a stop button and no seek bar`() {
        show(PlayerContent(track = track, isPlaying = true, isStation = true, showLyrics = true))

        composeRule.onNodeWithContentDescription(string(R.string.player_stop)).performClick()

        composeRule.onNodeWithContentDescription(string(R.string.player_lyrics)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.player_queue)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.player_share)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.player_like)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.player_live)).assertExists()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
        assertEquals(listOf<PlayerIntent>(PlayerIntent.StopClicked), intents)
    }

    @Test
    @Config(qualifiers = "w320dp-h470dp")
    fun `inline queue keeps playback controls available while selecting an upcoming track`() {
        show(PlayerContent(track = track, isPlaying = true, upNext = queue, sheet = PlayerSheet.Queue))

        composeRule.onNodeWithTag("playerQueue").performScrollToNode(hasText("Paper Moons"))
        composeRule.onNodeWithText("Paper Moons").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(string(R.string.ui_pause)).performClick()

        assertEquals(
            listOf(PlayerIntent.UpNextTrackClicked(position = 0), PlayerIntent.PlayPauseClicked),
            intents,
        )
    }

    @Test
    fun `instrumental hides lyrics even if the last song showed them`() {
        show(PlayerContent(track = track, lyrics = LyricsUi.None, showLyrics = true))
        composeRule.onNodeWithContentDescription(string(R.string.player_lyrics)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h470dp")
    fun `queue clear and autoplay dispatch their intents`() {
        show(PlayerContent(track = track, upNext = queue, sheet = PlayerSheet.Queue))
        composeRule.onNodeWithTag("playerQueue").performScrollToNode(hasText(string(R.string.player_clear)))
        composeRule.onNodeWithText(string(R.string.player_clear)).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("playerQueue").performScrollToNode(
            hasContentDescription(string(R.string.player_autoplay)),
        )
        composeRule.onNodeWithContentDescription(string(R.string.player_autoplay)).assertIsDisplayed().performClick()
        assertEquals(
            listOf(PlayerIntent.ClearUpNextClicked, PlayerIntent.ComingSoonClicked(SoonFeature.Autoplay)),
            intents,
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `normal phone queue shows header first track clear and transport together`() {
        show(PlayerContent(track = track, isPlaying = true, upNext = queue, sheet = PlayerSheet.Queue))
        composeRule.onNodeWithText("Glass Harbor").assertIsDisplayed()
        composeRule.onNodeWithText("Paper Moons").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.player_clear)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.ui_pause)).assertIsDisplayed()
    }

    private fun show(state: PlayerContent) {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                PlayerScreen(
                    state = state,
                    clock = { PlaybackClock(positionMs = 1_000, durationMs = 10_000) },
                    onIntent = { intents += it },
                )
            }
        }
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun queued(id: String, title: String) = TrackUi(
        id = TrackId(id),
        title = title,
        subtitle = UiText.Raw("Mira Vale"),
        coverSeed = title,
        status = TrackUi.Status.Ready,
    )
}
