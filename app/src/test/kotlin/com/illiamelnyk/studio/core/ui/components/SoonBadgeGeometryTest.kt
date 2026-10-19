package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoonBadgeGeometryTest {
    @get:Rule val rule = createComposeRule()

    @Test fun badgesReserveSpaceWithoutIncreasingControlHeight() = verify(1f)

    @Test fun badgesReserveSpaceAtLargeFont() = verify(2f)

    @Test fun badgesFitRightToLeftControls() = verify(1f, LayoutDirection.Rtl)

    @Test fun badgesFitRightToLeftControlsAtLargeFont() = verify(2f, LayoutDirection.Rtl)

    @Test fun horizontalScrollDoesNotClipBadges() = verifyStrip(1f)

    @Test fun horizontalScrollDoesNotClipBadgesAtLargeFont() = verifyStrip(2f)

    private fun verifyStrip(scale: Float) {
        val labels = listOf("Muse", "Styles", "Sounds")
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StudioTheme {
                    Column(Modifier.width(320.dp).padding(8.dp)) {
                        Row(Modifier.testTag("strip").horizontalScroll(rememberScrollState())) {
                            labels.forEach { label ->
                                StudioChip(
                                    label,
                                    {},
                                    Modifier.testTag(label),
                                    leadingIcon = StudioIcons.AutoAwesome,
                                    comingSoon = true,
                                )
                            }
                        }
                        StudioSoonBadge(Modifier.testTag("reference"))
                    }
                }
            }
        }
        val reference = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("reference")),
            useUnmergedTree = true,
        ).captureToImage()
        labels.forEach { label ->
            rule.onNodeWithTag(label).performScrollTo()
            val labelBounds = rule.onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Label remains visible in the scroll row", labelBounds.width > 0 && labelBounds.height > 0)
            val badge = rule.onNode(
                hasText("Soon") and hasAnyAncestor(hasTestTag(label)),
                useUnmergedTree = true,
            )
            val image = badge.captureToImage()
            assertEquals("Unclipped width in $label", reference.width, image.width)
            assertEquals("Unclipped height in $label", reference.height, image.height)
            val bounds = badge.fetchSemanticsNode().boundsInRoot
            val strip = rule.onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
            assertTrue("Badge remains within scroll viewport", bounds.top >= strip.top && bounds.bottom <= strip.bottom)
        }
    }

    private fun verify(scale: Float, direction: LayoutDirection = LayoutDirection.Ltr) {
        val soon = mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale),
                LocalLayoutDirection provides direction,
            ) {
                StudioTheme {
                    Column(Modifier.padding(24.dp).clipToBounds()) {
                        StudioIconButton(
                            StudioIcons.AutoAwesome,
                            "Enhance",
                            { clicks++ },
                            Modifier.testTag("icon"),
                            comingSoon = soon.value,
                        )
                        StudioChip(
                            "Styles",
                            {},
                            Modifier.testTag("chip"),
                            leadingIcon = StudioIcons.AutoAwesome,
                            comingSoon = soon.value,
                        )
                        StudioButton("Explain", {}, Modifier.testTag("button"), comingSoon = soon.value)
                        StudioSegmentedControl(
                            listOf("Create", "Muse"),
                            "Create",
                            {},
                            Modifier.testTag("segments"),
                            compact = true,
                            isComingSoon = { it == "Muse" && soon.value },
                        ) { it }
                        StudioSoonBadge(
                            Modifier.testTag("referenceBadge").background(StudioTheme.colors.surface.level2),
                        )
                    }
                }
            }
        }
        val tags = listOf("icon", "chip", "button", "segments")
        val before = tags.associateWith { rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val iconBefore = rule.onNodeWithContentDescription("Enhance", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { soon.value = true }
        tags.forEach { tag ->
            val after = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertEquals("Badge must not increase control height: $tag", before.getValue(tag).height, after.height)
            assertTrue(
                "Control must reserve horizontal space for its badge: $tag",
                after.width >= before.getValue(tag).width,
            )
            val badgeBounds = rule.onNode(
                hasText("Soon") and hasAnyAncestor(hasTestTag(tag)),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Badge stays within $tag: $badgeBounds / $after",
                badgeBounds.left >= after.left && badgeBounds.top >= after.top &&
                    badgeBounds.right <= after.right && badgeBounds.bottom <= after.bottom,
            )
        }
        val iconAfter = rule.onNodeWithContentDescription("Enhance", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(iconBefore.size, iconAfter.size)
        val badge = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("icon")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        if (direction == LayoutDirection.Ltr) {
            assertTrue("Badge must not cover the icon", badge.left >= iconAfter.right)
        } else {
            assertTrue("RTL badge must not cover the icon", badge.right <= iconAfter.left)
        }
        val chipBadge = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("chip")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val chipLabel = rule.onNodeWithText("Styles", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Chip label remains visible", chipLabel.width > 0 && chipLabel.height > 0)
        if (direction == LayoutDirection.Ltr) {
            assertTrue("Badge follows the label without overlap", chipBadge.left >= chipLabel.right)
        } else {
            assertTrue("RTL badge follows the label without overlap", chipBadge.right <= chipLabel.left)
        }
        val renderedBadge = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("chip")),
            useUnmergedTree = true,
        ).captureToImage().toPixelMap()
        val referenceBadge = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("referenceBadge")),
            useUnmergedTree = true,
        ).captureToImage().toPixelMap()
        assertEquals(referenceBadge.width, renderedBadge.width)
        assertEquals(referenceBadge.height, renderedBadge.height)
        var differentPixels = 0
        var maxDelta = 0f
        for (y in 0 until renderedBadge.height) {
            for (x in 0 until renderedBadge.width) {
                val actual = renderedBadge[x, y]
                val expected = referenceBadge[x, y]
                val delta =
                    maxOf(
                        abs(actual.red - expected.red),
                        abs(actual.green - expected.green),
                        abs(actual.blue - expected.blue),
                        abs(actual.alpha - expected.alpha),
                    )
                maxDelta = maxOf(maxDelta, delta)
                if (delta > 1f / 255f + 0.000001f) differentPixels++
            }
        }
        assertEquals("Badge rendering must match within one color step (maxDelta=$maxDelta)", 0, differentPixels)
        rule.onNodeWithTag("icon").performClick()
        assertEquals(1, clicks)
    }
}
