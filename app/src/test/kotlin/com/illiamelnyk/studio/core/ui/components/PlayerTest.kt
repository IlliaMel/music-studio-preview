package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `seek bar exposes its progress and seeks through accessibility`() {
        var seekedTo = -1f
        setThemedContent {
            SeekBar(progress = { 0.25f }, onSeek = { seekedTo = it }, elapsed = "00:30", remaining = "-01:30")
        }
        val seekBar = composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)))

        seekBar.performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(0.6f) }

        assertEquals(0.6f, seekedTo)
    }

    @Test
    fun `seek bar seeks where it is tapped`() {
        var seekedTo = -1f
        setThemedContent {
            SeekBar(progress = { 0f }, onSeek = { seekedTo = it }, elapsed = "00:00", remaining = "-02:00")
        }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f)))
            .performTouchInput { click(percentOffset(0.75f, 0.5f)) }

        assertEquals(0.75f, seekedTo, 0.01f)
    }

    @Test
    fun `tapping a lyric line seeks to its start`() {
        var seekedTo = -1L
        setThemedContent {
            Box(Modifier.height(LyricsHeight)) {
                LyricsView(lines = Lines, positionMs = { 0 }, onSeek = { seekedTo = it })
            }
        }

        composeRule.onNodeWithText("Hold on to the night").performClick()

        assertEquals(LINE_GAP_MS, seekedTo)
    }

    @Test
    fun `lyrics keep the current line in full view on the upper third`() {
        var position by mutableLongStateOf(0L)
        setLyrics { position }

        listOf(1, 2, 15, 16, ManyLines.lastIndex).forEach { line ->
            position = LINE_GAP_MS * line
            composeRule.waitForIdle()
            assertLineAtFocus(line)
        }
    }

    @Test
    fun `scrolling the lyrics by hand pauses the auto-scroll for three seconds`() {
        var position by mutableLongStateOf(LINE_GAP_MS * 2)
        setLyrics { position }
        assertLineAtFocus(2)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(LYRICS).performTouchInput { swipeUp() }
        position = LINE_GAP_MS * 3
        composeRule.mainClock.advanceTimeBy(LyricsDefaults.MANUAL_SCROLL_PAUSE_MS - SETTLE_MS)
        assertFalse("line 3 followed during the pause", isLineAtFocus(3))

        composeRule.mainClock.advanceTimeBy(SETTLE_MS * 2)
        assertLineAtFocus(3)
    }

    @Test
    fun `mini player progress ticks without recomposing its caller`() {
        var progress by mutableFloatStateOf(0f)
        var compositions = 0
        setThemedContent {
            SideEffect { compositions++ }
            MiniPlayer(
                title = "Night Drive",
                subtitle = "Studio",
                coverSeed = "Night Drive",
                isPlaying = true,
                progress = { progress },
                onClick = {},
                onPlayPause = {},
            )
        }

        repeat(TICKS) { tick ->
            progress = tick / TICKS.toFloat()
            composeRule.waitForIdle()
        }

        assertEquals(1, compositions)
    }

    private fun setLyrics(position: () -> Long) {
        setThemedContent {
            Box(
                Modifier
                    .testTag(LYRICS)
                    .height(LyricsHeight),
            ) {
                LyricsView(lines = ManyLines, positionMs = position, onSeek = {})
            }
        }
    }

    private fun isLineAtFocus(line: Int): Boolean {
        val view = composeRule.onNodeWithTag(LYRICS).fetchSemanticsNode().boundsInRoot
        val text = composeRule.onAllNodesWithText("Line $line").fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            ?: return false
        val focus = view.top + view.height * LyricsDefaults.FOCUS_LINE
        return text.top >= view.top && text.bottom <= view.bottom && abs(text.top - focus) <= FOCUS_TOLERANCE_PX
    }

    private fun assertLineAtFocus(line: Int) {
        assertTrue("line $line is not in full view on the upper third", isLineAtFocus(line))
    }

    private fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent { StudioTheme(darkTheme = true, content = content) }
    }

    private companion object {
        const val LINE_GAP_MS = 4_000L
        const val TICKS = 10
        const val LYRICS = "lyrics"
        const val SETTLE_MS = 1_000L
        const val FOCUS_TOLERANCE_PX = 2f
        val LyricsHeight = 320.dp

        val Lines = listOf(
            LyricLine(0, listOf(LyricWord("Radio", 0), LyricWord("is", 400), LyricWord("playing", 700))),
            LyricLine(
                LINE_GAP_MS,
                listOf("Hold", "on", "to", "the", "night").mapIndexed { i, word ->
                    LyricWord(
                        word,
                        LINE_GAP_MS + i * 300L,
                    )
                },
            ),
        )

        val ManyLines = (0 until 30).map { index ->
            LyricLine(
                index * LINE_GAP_MS,
                listOf(
                    LyricWord("Line", index * LINE_GAP_MS),
                    LyricWord(
                        "$index",
                        index * LINE_GAP_MS,
                    ),
                ),
            )
        }
    }
}
