package com.illiamelnyk.studio.feature.player.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        composeRule.onNodeWithText(string(R.string.player_like)).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(
            string(R.string.player_lyrics),
        ).performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithContentDescription(
            string(R.string.ui_pause),
        ).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(
            string(R.string.player_queue),
        ).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText(string(R.string.player_remix)).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(
            string(R.string.player_collapse),
        ).performScrollTo().assertIsDisplayed().performClick()

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
        tapReachable(composeRule.onNodeWithText(string(R.string.player_clear)))
        tapReachable(composeRule.onNodeWithContentDescription(string(R.string.player_autoplay)))
        assertEquals(
            listOf(PlayerIntent.ClearUpNextClicked, PlayerIntent.AutoplayChanged(true)),
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

    @Test
    @Config(qualifiers = "w320dp-h400dp")
    fun `compact player keeps long title actions reachable`() {
        show(PlayerContent(track = track.copy(title = "A very long track title across two lines"), isPlaying = true))
        composeRule.onNodeWithText(string(R.string.player_like)).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(
            string(R.string.ui_pause),
        ).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(PlayerIntent.LikeClicked, PlayerIntent.PlayPauseClicked), intents)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun ordinaryCompactPlayerKeepsActionsTouchableAtFont13() = assertCompactActions(1.3f)

    @Test
    @Config(qualifiers = "w320dp-h400dp")
    fun ordinaryCompactPlayerKeepsActionsTouchableAtFont2() = assertCompactActions(2f)

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun compactQueueKeepsClearRowsAndPlaybackTouchableAtFont13() = assertCompactQueue(1.3f)

    @Test
    @Config(qualifiers = "w320dp-h400dp")
    fun compactQueueKeepsClearRowsAndPlaybackTouchableAtFont2() = assertCompactQueue(2f)

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun compactLiveKeepsStopTouchableAtFont13() = assertCompactLive(1.3f)

    @Test
    @Config(qualifiers = "w320dp-h400dp")
    fun compactLiveKeepsStopTouchableAtFont2() = assertCompactLive(2f)

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun rawRecordingHidesRemixInFullActionsAndMoreMenuWhileShareStillWorks() {
        val recording = com.illiamelnyk.studio.feature.player.fake.track(
            "recording-raw-fixture",
            20.seconds,
        )
        val projected = PlayerContent().withPlayback(
            com.illiamelnyk.studio.core.domain.playback.PlaybackState(queue = listOf(recording)),
        )
        show(projected)
        composeRule.onNodeWithText(string(R.string.player_remix)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.player_add_to)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.player_more)).performClick()
        composeRule.onNodeWithText(string(R.string.player_remix)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.player_share)).assertIsDisplayed().performTouchInput { click() }
        assertEquals(listOf(PlayerIntent.ShareClicked), intents)
    }

    private fun assertCompactActions(fontScale: Float) {
        show(
            PlayerContent(track = track.copy(title = "A very long track title across two lines"), isPlaying = true),
            fontScale,
            PaddingValues(vertical = 24.dp),
        )
        val scrolls = tapReachable(composeRule.onNodeWithText(string(R.string.player_like))) +
            tapReachable(composeRule.onNodeWithContentDescription(string(R.string.ui_pause)))
        if (fontScale >= 2f) assertTrue("Font 2 must exercise real scrolling", scrolls > 0)
        assertEquals(listOf(PlayerIntent.LikeClicked, PlayerIntent.PlayPauseClicked), intents)
    }

    private fun assertCompactQueue(fontScale: Float) {
        show(
            PlayerContent(track = track, isPlaying = true, upNext = queue, sheet = PlayerSheet.Queue),
            fontScale,
            PaddingValues(vertical = 24.dp),
        )
        val scrolls = tapReachable(composeRule.onNodeWithText(string(R.string.player_clear))) +
            tapReachable(composeRule.onNodeWithText("Paper Moons"), composeRule.onNodeWithTag("playerQueue")) +
            tapReachable(composeRule.onNodeWithContentDescription(string(R.string.ui_pause)))
        if (fontScale >= 2f) assertTrue("Font 2 must exercise real scrolling", scrolls > 0)
        assertEquals(
            listOf(PlayerIntent.ClearUpNextClicked, PlayerIntent.UpNextTrackClicked(0), PlayerIntent.PlayPauseClicked),
            intents,
        )
    }

    private fun assertCompactLive(fontScale: Float) {
        show(
            PlayerContent(
                track = track.copy(title = "A long station title across two lines"),
                isPlaying = true,
                isStation = true,
            ),
            fontScale,
            PaddingValues(vertical = 24.dp),
        )
        val scrolls = tapReachable(composeRule.onNodeWithContentDescription(string(R.string.player_stop)))
        if (fontScale >= 2f) assertTrue("Font 2 must exercise real scrolling", scrolls > 0)
        assertEquals(listOf(PlayerIntent.StopClicked), intents)
    }

    private fun tapReachable(
        node: SemanticsNodeInteraction,
        scrollContainer: SemanticsNodeInteraction = composeRule.onRoot(),
    ): Int {
        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        fun fullyVisible(): Boolean {
            val target = node.fetchSemanticsNode()
            val position = target.positionInRoot
            return target.size.width > 0 && target.size.height > 0 &&
                position.y >= viewport.top && position.y + target.size.height <= viewport.bottom &&
                position.x >= viewport.left && position.x + target.size.width <= viewport.right &&
                target.boundsInRoot.width + 1 >= target.size.width &&
                target.boundsInRoot.height + 1 >= target.size.height
        }
        var swipes = 0
        while (!fullyVisible() && swipes < 6) {
            val target = node.fetchSemanticsNode()
            val above = target.positionInRoot.y < viewport.top ||
                target.positionInRoot.y < target.boundsInRoot.top
            scrollContainer.performTouchInput { if (above) swipeDown() else swipeUp() }
            composeRule.waitForIdle()
            swipes++
        }
        val target = node.fetchSemanticsNode()
        assertTrue(
            "Control after $swipes swipes: position=" + target.positionInRoot + " size=" + target.size +
                " clipped=" + target.boundsInRoot + " viewport=" + viewport,
            fullyVisible(),
        )
        node.assertIsDisplayed().performTouchInput { click() }
        return swipes
    }

    private fun show(state: PlayerContent, fontScale: Float = 1f, contentPadding: PaddingValues = PaddingValues()) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StudioTheme(darkTheme = true) {
                    PlayerScreen(
                        state = state,
                        clock = { PlaybackClock(positionMs = 1_000, durationMs = 10_000) },
                        onIntent = { intents += it },
                        contentPadding = contentPadding,
                    )
                }
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
