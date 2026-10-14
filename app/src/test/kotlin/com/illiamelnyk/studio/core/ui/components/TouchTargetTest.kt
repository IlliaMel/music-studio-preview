package com.illiamelnyk.studio.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * Every interactive component can be touched within 48 dp and tells TalkBack what kind of control
 * it is.
 *
 * Two checks: the touch bounds that accessibility services see, and, for components drawn smaller
 * than 48 dp, the space their layout reserves, so that no neighbor can sit in the extended touch area.
 */
@RunWith(RobolectricTestRunner::class)
class TouchTargetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val touchTarget = StudioDimens().size.touchTarget

    @Test
    fun `buttons of every size have a 48 dp touch target`() {
        setThemedContent {
            StudioButtonSize.entries.forEach { size ->
                Host(size.name) { StudioButton(size.name, onClick = {}, size = size) }
            }
        }

        StudioButtonSize.entries.forEach { size ->
            composeRule.onNodeWithText(size.name).assertTouchTarget(Role.Button)
            assertReservesTouchTarget(host = size.name)
        }
    }

    @Test
    fun `icon buttons of every size have a 48 dp touch target`() {
        setThemedContent {
            StudioIconButtonSize.entries.forEach { size ->
                Host(size.name) { StudioIconButton(StudioIcons.Search, size.name, onClick = {}, size = size) }
            }
        }

        StudioIconButtonSize.entries.forEach { size ->
            composeRule.onNodeWithContentDescription(size.name).assertTouchTarget(Role.Button)
            assertReservesTouchTarget(host = size.name)
        }
    }

    @Test
    fun `chips and the remove button of an input chip have a 48 dp touch target`() {
        setThemedContent {
            Host("input") {
                StudioChip(
                    text = "Light Through Clouds",
                    onClick = {},
                    leadingAvatar = {
                        Cover(
                            "Light Through Clouds",
                            StudioTheme.dimens.size.avatar.xs,
                            shape = StudioTheme.shapes.full,
                        )
                    },
                    onRemove = {},
                )
            }
            Host("filter") { StudioFilterChip("Liked", selected = false, onClick = {}) }
        }

        composeRule.onNodeWithText("Light Through Clouds").assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_remove, "Light Through Clouds"))
            .assertTouchTarget(Role.Button)
        composeRule.onNodeWithText("Liked").assertTouchTarget(Role.Checkbox)
        assertReservesTouchTarget(host = "input")
        assertReservesTouchTarget(host = "filter")
    }

    @Test
    fun `segments of both segmented controls have a 48 dp touch target`() {
        setThemedContent {
            Host("regular") {
                StudioSegmentedControl(listOf("Create", "Listen"), selected = "Create", onSelect = {}) { it }
            }
            Host("compact") {
                StudioSegmentedControl(listOf("Auto", "Deep"), selected = "Auto", onSelect = {}, compact = true) { it }
            }
        }

        listOf("Create", "Listen", "Auto", "Deep").forEach { option ->
            composeRule.onNodeWithText(option).assertTouchTarget(Role.Tab)
        }
        assertReservesTouchTarget(host = "regular")
        assertReservesTouchTarget(host = "compact")
    }

    @Test
    fun `top bar avatar has a 48 dp touch target`() {
        setThemedContent {
            Host("avatar") { TopBarAvatar(seed = "Illia", contentDescription = "Profile", onClick = {}) }
        }

        composeRule.onNodeWithContentDescription("Profile").assertTouchTarget(Role.Button)
        assertReservesTouchTarget(host = "avatar")
    }

    @Test
    fun `navigation bar items have a 48 dp touch target`() {
        setThemedContent {
            StudioNavigationBar(
                items = listOf(
                    StudioNavigationItem("Create", StudioIcons.AutoAwesome),
                    StudioNavigationItem("Listen", StudioIcons.Headphones),
                ),
                selectedIndex = 0,
                onSelect = {},
            )
        }

        composeRule.onNodeWithText("Create").assertTouchTarget(Role.Tab)
        composeRule.onNodeWithText("Listen").assertTouchTarget(Role.Tab)
    }

    @Test
    fun `track rows and their actions have a 48 dp touch target`() {
        setThemedContent {
            TrackRow(
                title = "Rust and Redemption",
                subtitle = "Enrique Flores",
                coverSeed = "Rust and Redemption",
                onClick = {},
                onMore = {},
            )
            TrackRow(
                title = "Song in the style of…",
                subtitle = "",
                coverSeed = "Song in the style of…",
                onClick = {},
                state = TrackRowState.Failed("Prompt rejected"),
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Rust and Redemption").assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_more_options)).assertTouchTarget(Role.Button)
        composeRule.onNodeWithContentDescription(string(R.string.ui_retry)).assertTouchTarget(Role.Button)
    }

    private fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                Column { content() }
            }
        }
    }

    /** Takes exactly the space that the layout of [content] asks for, to measure it from outside. */
    @Composable
    private fun Host(tag: String, content: @Composable () -> Unit) {
        Box(Modifier.testTag(tag)) { content() }
    }

    private fun SemanticsNodeInteraction.assertTouchTarget(role: Role) {
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, role))
        val bounds = fetchSemanticsNode().touchBoundsInRoot
        with(composeRule.density) {
            assertTrue("touch width ${bounds.width.toDp()}", bounds.width.toDp() >= touchTarget)
            assertTrue("touch height ${bounds.height.toDp()}", bounds.height.toDp() >= touchTarget)
        }
    }

    private fun assertReservesTouchTarget(host: String) {
        composeRule.onNodeWithTag(host)
            .assertWidthIsAtLeast(touchTarget)
            .assertHeightIsAtLeast(touchTarget)
    }

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)
}
