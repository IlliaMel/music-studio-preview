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
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.bleed
import com.illiamelnyk.studio.feature.create.presentation.label
import kotlin.time.Duration

/**
 * The options row of the expanded composer: quality, styles coming soon, the removable instrumental
 * chip, the track length and how many tracks a prompt makes. It
 * scrolls sideways under the screen edges when it doesn't fit.
 */
@Composable
fun ComposerOptions(
    quality: Quality,
    lyricsMode: LyricsMode,
    duration: DurationOptionUi?,
    variants: Int,
    onQualitySelect: (Quality) -> Unit,
    onStylesClick: () -> Unit,
    onInstrumentalRemove: () -> Unit,
    onDurationSelect: (Duration?) -> Unit,
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
        StudioChip(
            stringResource(R.string.create_chip_styles),
            onStylesClick,
            leadingIcon = StudioIcons.AutoAwesome,
            comingSoon = true,
        )
        if (lyricsMode == LyricsMode.Instrumental) {
            StudioChip(
                text = stringResource(R.string.create_settings_instrumental),
                onClick = onInstrumentalRemove,
                onRemove = onInstrumentalRemove,
            )
        }
        ComposerDurationMenu(duration, onDurationSelect)
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
            lyricsMode = LyricsMode.Instrumental,
            duration = null,
            variants = 2,
            onQualitySelect = {},
            onStylesClick = {},
            onInstrumentalRemove = {},
            onDurationSelect = {},
            onVariantsClick = {},
        )
    }
}
