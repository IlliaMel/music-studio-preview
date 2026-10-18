package com.illiamelnyk.studio.feature.listen.presentation.collection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun CollectionActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().background(StudioTheme.colors.background).padding(StudioTheme.dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs, Alignment.CenterHorizontally),
    ) {
        StudioButton(
            stringResource(R.string.collection_play),
            onClick = onPlay,
            enabled = enabled,
            style = StudioButtonStyle.Secondary,
            icon = StudioIcons.PlayArrowFilled,
        )
        StudioButton(
            stringResource(R.string.collection_shuffle),
            onClick = onShuffle,
            enabled = enabled,
            style = StudioButtonStyle.Secondary,
            icon = StudioIcons.Shuffle,
        )
    }
}
