package com.illiamelnyk.studio.core.presentation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.TopBar
import com.illiamelnyk.studio.core.ui.icons.StudioIcons

/**
 * The [TopBar] of a screen opened on top of a tab: a back button, an optional centered [title]
 * and [actions].
 */
@Composable
fun BackTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val navigation = @Composable {
        StudioIconButton(
            icon = StudioIcons.ChevronLeft,
            contentDescription = stringResource(R.string.nav_back),
            onClick = onBack,
            style = StudioIconButtonStyle.Surface,
        )
    }
    if (title == null) {
        TopBar(modifier = modifier, navigation = navigation, actions = actions) {}
    } else {
        TopBar(title = title, modifier = modifier, navigation = navigation, actions = actions)
    }
}
