package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.ComingSoonSheetContent
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The sheet of a [ComingSoonFeature], open while [feature] isn't `null`. It keeps showing the last
 * feature while it slides away.
 */
@Composable
fun ComingSoonSheet(
    feature: ComingSoonFeature?,
    onWantClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLatest(feature)
    OverlaySheet(visible = feature != null, onDismissRequest = onDismissRequest, modifier = modifier) {
        if (shown != null) ComingSoonContent(shown, onWantClick)
    }
}

@Composable
private fun ComingSoonContent(feature: ComingSoonFeature, onWantClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(feature.title)
    ComingSoonSheetContent(
        title = title,
        description = stringResource(feature.description),
        steps = stringArrayResource(feature.steps).toList(),
        actionLabel = stringResource(R.string.create_soon_want),
        onAction = onWantClick,
        modifier = modifier,
        icon = feature.icon,
        heroSeed = title,
    )
}

private class ComingSoonPreviewData : PreviewParameterProvider<ComingSoonFeature> {
    override val values = ComingSoonFeature.entries.asSequence()
}

@PreviewLightDark
@Composable
private fun ComingSoonContentPreview(@PreviewParameter(ComingSoonPreviewData::class) feature: ComingSoonFeature) {
    PreviewScreen {
        Column(Modifier.padding(StudioTheme.dimens.spacing.m)) { ComingSoonContent(feature, onWantClick = {}) }
    }
}
