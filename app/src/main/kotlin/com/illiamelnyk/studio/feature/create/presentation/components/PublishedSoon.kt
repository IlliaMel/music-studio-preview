package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** The published tracks, which aren't there yet: what publishing will be and how it will work. */
@Composable
fun PublishedSoon(onHowClick: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = StudioIcons.IosShare,
        title = stringResource(R.string.create_published_title),
        message = stringResource(R.string.create_published_message),
        modifier = modifier
            .fillMaxWidth()
            .padding(StudioTheme.dimens.spacing.xxl),
    ) {
        StudioButton(
            text = stringResource(R.string.create_published_how),
            onClick = onHowClick,
            style = StudioButtonStyle.Secondary,
            size = StudioButtonSize.Small,
        )
    }
}

@PreviewLightDark
@Composable
private fun PublishedSoonPreview() {
    PreviewScreen { PublishedSoon(onHowClick = {}) }
}
