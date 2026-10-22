package com.illiamelnyk.studio.feature.create.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.OverlaySheet
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class ComposerOptionsLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun optionsAndEditorMoveTogetherOnEveryExpansionFrame() {
        compose.setContent {
            StudioTheme {
                Box(Modifier.size(360.dp, 600.dp), contentAlignment = Alignment.BottomCenter) {
                    CreateComposer(ComposerUi(), {})
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNode(hasSetTextAction()).performClick()
        val samples = mutableListOf<Triple<Long, Float, Float>>()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val options = compose.onAllNodesWithTag("composerOptions").fetchSemanticsNodes().firstOrNull()
            if (options != null && options.boundsInRoot.height > 1f) {
                val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
                samples += Triple(
                    compose.mainClock.currentTime,
                    options.positionInRoot.y + options.size.height,
                    editor.positionInRoot.y,
                )
            }
        }
        println("COMPOSER_FRAME_TRACE=" + samples.joinToString(";") { "${it.first},${it.second},${it.third}" })
        assertTrue("Intermediate animation frames must be sampled", samples.size > 12)
        val gaps = samples.map { it.third - it.second }
        assertTrue("Options/editor gap drift: " + (gaps.max() - gaps.min()), gaps.max() - gaps.min() <= 1f)
    }

    @Test
    fun recorderDoesNotCollapseTheComposerWhenTheEditorLosesFocus() {
        val recording = mutableStateOf(false)
        compose.setContent {
            StudioTheme {
                val focus = LocalFocusManager.current
                CreateComposer(ComposerUi(optionsReady = true, audioReferencesSupported = true), onIntent = {
                    if (it == CreateIntent.ReferenceClicked) {
                        focus.clearFocus()
                        recording.value = true
                    }
                })
                OverlaySheet(recording.value, { recording.value = false }) { BasicText("Recorder preview") }
            }
        }
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNodeWithTag("composerOptions").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.create_composer_upload)).performClick()
        compose.onNodeWithText("Recorder preview").assertIsDisplayed()
        compose.runOnIdle { recording.value = false }
        compose.onNodeWithTag("composerOptions").assertIsDisplayed()
    }

    @Test
    fun optionsUseTheWholeViewportWhileTheEditorAndFirstControlStayInset() = assertOptionsLayout(1f)

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun narrowPhoneWithLargeTextKeepsTheLastControlReachableWithoutAnInsetClip() = assertOptionsLayout(1.6f)

    private fun assertOptionsLayout(fontScale: Float) {
        val intents = mutableListOf<CreateIntent>()
        var insetPx = 0f
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StudioTheme {
                    insetPx = with(LocalDensity.current) { StudioTheme.dimens.layout.screenPadding.toPx() }
                    CreateComposer(
                        ComposerUi(prompt = "Warm piano with gentle rain", lyricsMode = LyricsMode.Instrumental),
                        onIntent = { intents += it },
                    )
                }
            }
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("composerOptions").fetchSemanticsNode().boundsInRoot
        val quality = compose.onNodeWithTag("composerQuality").fetchSemanticsNode().boundsInRoot
        val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(root.left, viewport.left, 1f)
        assertEquals(root.right, viewport.right, 1f)
        assertEquals(root.left + insetPx, quality.left, 1f)
        assertTrue(
            "The text field keeps its own comfortable side insets",
            editor.left > viewport.left && editor.right < viewport.right,
        )

        val instrumental = compose.onNodeWithText(
            compose.activity.getString(R.string.create_settings_instrumental),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The removable chip must show its label, not only its close icon", instrumental.width > 0f)

        compose.onNodeWithTag("composerOptions").performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(10_000f, 0f)
        }
        val variantDescription = compose.activity.resources.getQuantityString(
            R.plurals.create_variants_description,
            1,
            1,
        )
        val variants = compose.onNodeWithContentDescription(variantDescription).assertIsDisplayed()
        val lastBounds = variants.fetchSemanticsNode().boundsInRoot
        assertTrue("The last control keeps its trailing inset", lastBounds.right <= root.right - insetPx + 1f)
        assertTrue("The last control stays fully visible", lastBounds.left >= root.left)
        val range = compose.onNodeWithTag(
            "composerOptions",
        ).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        if (range.maxValue() > 0f) assertEquals(root.right - insetPx, lastBounds.right, 1f)
        variants.performClick()
        assertEquals(listOf(CreateIntent.VariantsClicked), intents)
    }
}
