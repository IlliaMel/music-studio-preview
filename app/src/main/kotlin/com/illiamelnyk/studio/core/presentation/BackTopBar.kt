package com.illiamelnyk.studio.core.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.TopBar
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

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
        TopBar(title = title, modifier = modifier, navigation = navigation, actions = {
            Row(Modifier.widthIn(min = StudioTheme.dimens.size.touchTarget), content = actions)
        })
    }
}
