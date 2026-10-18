package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
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

    @Test fun badgesKeepControlAndIconGeometry() = verify(1f)

    @Test fun badgesKeepControlAndIconGeometryAtLargeFont() = verify(2f)

    private fun verify(scale: Float) {
        val soon = mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StudioTheme {
                    Column(Modifier.padding(24.dp)) {
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
            assertEquals(
                "Badge must not resize $tag",
                before.getValue(tag),
                rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot,
            )
        }
        val iconAfter = rule.onNodeWithContentDescription("Enhance", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(iconBefore, iconAfter)
        val badge = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("icon")),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        assertTrue(badge.boundsInRoot.center.x > iconAfter.center.x)
        val cornerOverlap = with(rule.density) { 2.dp.toPx() } + 1f
        assertTrue(
            "Badge must grow outward, away from the icon",
            badge.boundsInRoot.left >= iconAfter.right - cornerOverlap,
        )
        assertTrue("Badge must not cover the icon", badge.boundsInRoot.bottom <= iconAfter.top + cornerOverlap)
        assertTrue(badge.boundsInRoot.center.y < iconAfter.center.y)
        val chipBadge = rule.onNode(
            hasText("Soon") and hasAnyAncestor(hasTestTag("chip")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val chipLabel = rule.onNodeWithText("Styles", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Badge must stay above the adjacent label: badge=$chipBadge label=$chipLabel overlap=$cornerOverlap",
            chipBadge.bottom <= chipLabel.top + 1f,
        )
        rule.onNodeWithTag("icon").performClick()
        assertEquals(1, clicks)
    }
}
