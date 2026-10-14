package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.StyleUi
import com.illiamelnyk.studio.feature.create.presentation.common.bleed
import com.illiamelnyk.studio.feature.create.presentation.label

/**
 * The options row of the expanded composer: quality, the style (a chip with the style's art and a
 * remove button once one is picked), the track length and how many tracks a prompt makes. It
 * scrolls sideways under the screen edges when it doesn't fit.
 */
@Composable
fun ComposerOptions(
    quality: Quality,
    style: StyleUi?,
    duration: String,
    variants: Int,
    onQualitySelect: (Quality) -> Unit,
    onStylesClick: () -> Unit,
    onStyleRemove: () -> Unit,
    onDurationClick: () -> Unit,
    onVariantsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The row scrolls under the screen edges, past the composer's padding.
            .bleed(dimens.layout.screenPadding)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioSegmentedControl(
            options = Quality.entries,
            selected = quality,
            onSelect = onQualitySelect,
            compact = true,
        ) { option -> stringResource(option.label) }
        if (style == null) {
            StudioChip(
                stringResource(R.string.create_chip_styles),
                onStylesClick,
                leadingIcon = StudioIcons.AutoAwesome,
            )
        } else {
            StudioChip(
                text = style.name,
                onClick = onStylesClick,
                leadingAvatar = { Cover(style.coverSeed, dimens.size.avatar.xs, shape = StudioTheme.shapes.full) },
                onRemove = onStyleRemove,
            )
        }
        val durationDescription = stringResource(R.string.create_length_description, duration)
        StudioChip(
            text = duration,
            onClick = onDurationClick,
            modifier = Modifier.semantics { contentDescription = durationDescription },
            leadingIcon = StudioIcons.Schedule,
        )
        val variantsDescription = pluralStringResource(R.plurals.create_variants_description, variants, variants)
        StudioChip(
            text = stringResource(R.string.create_variants, variants),
            onClick = onVariantsClick,
            modifier = Modifier.semantics { contentDescription = variantsDescription },
            leadingIcon = StudioIcons.FilterNone,
        )
    }
}

@PreviewLightDark
@Composable
private fun ComposerOptionsPreview() {
    PreviewScreen {
        ComposerOptions(
            quality = Quality.Deep,
            style = StyleUi("lofi", "Lo-fi", "Dusty drums · Rhodes", "Lo-fi"),
            duration = "2:00",
            variants = 2,
            onQualitySelect = {},
            onStylesClick = {},
            onStyleRemove = {},
            onDurationClick = {},
            onVariantsClick = {},
        )
    }
}
