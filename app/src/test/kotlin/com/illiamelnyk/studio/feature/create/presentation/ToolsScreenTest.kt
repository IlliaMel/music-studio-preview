package com.illiamelnyk.studio.feature.create.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsFilter
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsIntent
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsScreen
import com.illiamelnyk.studio.feature.create.presentation.tools.ToolsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Featured filter sends the selected filter`() {
        var intent: ToolsIntent? = null
        compose.setContent { StudioTheme { ToolsScreen(ToolsState(), { intent = it }) } }
        compose.onNodeWithText("Featured").performClick()
        assertEquals(ToolsIntent.FilterSelected(ToolsFilter.Featured), intent)
    }
}
