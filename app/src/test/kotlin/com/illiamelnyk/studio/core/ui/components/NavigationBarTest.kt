package com.illiamelnyk.studio.core.ui.components

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioDimens
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `navigation bar runs behind the system bar and keeps its destinations above it`() {
        composeRule.setContent {
            StudioTheme(darkTheme = true) {
                StudioNavigationBar(
                    items = listOf(StudioNavigationItem("Create", StudioIcons.AutoAwesome)),
                    selectedIndex = 0,
                    onSelect = {},
                    modifier = Modifier.testTag(BAR),
                )
            }
        }

        val systemBar = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, SYSTEM_BAR_PX))
            .build()
        composeRule.runOnUiThread {
            val content = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), systemBar)
        }
        composeRule.waitForIdle()

        val height = composeRule.onNodeWithTag(BAR).fetchSemanticsNode().size.height
        val destinations = with(composeRule.density) { StudioDimens().size.component.navigationBar.roundToPx() }
        assertEquals(destinations + SYSTEM_BAR_PX, height)
    }

    private companion object {
        const val BAR = "bar"
        const val SYSTEM_BAR_PX = 120
    }
}
