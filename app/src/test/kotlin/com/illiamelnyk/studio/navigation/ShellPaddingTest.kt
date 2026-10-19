package com.illiamelnyk.studio.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShellPaddingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun measuredHeaderUpdatesExistingRootPaddingProvider() {
        val bottom = BottomChrome()
        val top = TopChrome()
        var padding: ShellPadding? = null
        var initialBarHeight: Dp = 0.dp
        rule.setContent {
            StudioTheme {
                padding = rememberShellPadding(bottom, top)
                initialBarHeight = StudioTheme.dimens.size.component.topBar
            }
        }
        rule.runOnIdle {
            val current = requireNotNull(padding)
            val systemTop = current.screen.calculateTopPadding()
            assertEquals(systemTop + initialBarHeight, current.tabRoot.calculateTopPadding())
            val expandedHeight = systemTop + 104.dp
            top.height = expandedHeight
            assertEquals(expandedHeight, current.tabRoot.calculateTopPadding())
            top.height = systemTop + 56.dp
            assertEquals(systemTop + 56.dp, current.tabRoot.calculateTopPadding())
            assertSame(current, padding)
        }
    }

    @Test fun screensWithTheirOwnHeaderKeepSystemPaddingAndBottomChrome() {
        val bottom = BottomChrome()
        val top = TopChrome()
        var padding: ShellPadding? = null
        rule.setContent {
            StudioTheme { padding = rememberShellPadding(bottom, top) }
        }
        rule.runOnIdle {
            val current = requireNotNull(padding)
            val screenTop = current.screen.calculateTopPadding()
            top.height = screenTop + 104.dp
            bottom.height = 200.dp
            assertEquals(screenTop, current.screen.calculateTopPadding())
            assertEquals(200.dp, current.screen.calculateBottomPadding())
            assertEquals(200.dp, current.tabRoot.calculateBottomPadding())
        }
    }
}
