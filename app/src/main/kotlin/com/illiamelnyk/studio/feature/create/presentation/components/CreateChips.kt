package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The shortcuts at the top of Create: Muse, Styles, Sounds and Record. It scrolls sideways when
 * the chips don't fit, such as with a large font.
 */
@Composable
fun CreateChips(
    onMuseClick: () -> Unit,
    onStylesClick: () -> Unit,
    onSoundsClick: () -> Unit,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.xxs)),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        StudioChip(stringResource(R.string.create_soon_muse), onMuseClick, leadingIcon = StudioIcons.Forum)
        StudioChip(stringResource(R.string.create_chip_styles), onStylesClick, leadingIcon = StudioIcons.AutoAwesome)
        StudioChip(stringResource(R.string.create_chip_sounds), onSoundsClick, leadingIcon = StudioIcons.Waveform)
        StudioChip(stringResource(R.string.create_chip_record), onRecordClick, leadingIcon = StudioIcons.Mic)
    }
}

@PreviewLightDark
@Composable
private fun CreateChipsPreview() {
    PreviewScreen { CreateChips(onMuseClick = {}, onStylesClick = {}, onSoundsClick = {}, onRecordClick = {}) }
}
