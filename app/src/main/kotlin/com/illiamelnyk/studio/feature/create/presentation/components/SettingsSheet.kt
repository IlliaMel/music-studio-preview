package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData
import com.illiamelnyk.studio.feature.create.presentation.SettingsUi
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.OverlaySheet
import com.illiamelnyk.studio.feature.create.presentation.common.rememberLatest
import com.illiamelnyk.studio.feature.create.presentation.label
import kotlin.time.Duration

/** The callbacks of the settings sheet, one per choice. */
@Stable
class SettingsCallbacks(
    val onQualitySelect: (Quality) -> Unit,
    val onVariantsSelect: (Int) -> Unit,
    val onInstrumentalChange: (Boolean) -> Unit,
    val onDurationSelect: (Duration?) -> Unit,
    val onReset: () -> Unit,
    val onApply: () -> Unit,
    val onComingSoon: (ComingSoonFeature) -> Unit = {},
)

/**
 * The composer's settings, open while [draft] isn't `null`: vocals or not, the length
 * and the writing engine. Choices apply to the composer only with Apply; Reset brings back the defaults.
 */
@Composable
fun SettingsSheet(
    draft: SettingsUi?,
    callbacks: SettingsCallbacks,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLatest(draft)
    OverlaySheet(visible = draft != null, onDismissRequest = onDismissRequest, modifier = modifier) {
        if (shown != null) SettingsContent(shown, callbacks, onDismissRequest)
    }
}

@Composable
private fun SettingsContent(
    draft: SettingsUi,
    callbacks: SettingsCallbacks,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.l),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StudioText(stringResource(R.string.create_settings_title), StudioTheme.typography.titleMedium)
            StudioIconButton(
                StudioIcons.Close,
                stringResource(R.string.ui_close_sheet),
                onDismissRequest,
                Modifier.align(Alignment.CenterEnd),
            )
        }
        SettingsGroup(R.string.create_settings_length) {
            ComposerDurationMenu(draft.duration, callbacks.onDurationSelect)
        }
        SettingsGroup(R.string.create_settings_variations) {
            StudioSegmentedControl(listOf(1, 2), draft.variants, callbacks.onVariantsSelect) {
                stringResource(R.string.create_variants, it)
            }
        }
        SettingsGroup(R.string.create_composer_lyrics) {
            StudioSegmentedControl(
                options = listOf(
                    R.string.create_quality_auto,
                    R.string.create_lyrics_custom,
                    R.string.create_settings_instrumental,
                ),
                selected = if (draft.instrumental) R.string.create_settings_instrumental else R.string.create_quality_auto,
                onSelect = { option ->
                    if (option == R.string.create_lyrics_custom) {
                        callbacks.onComingSoon(ComingSoonFeature.Lyrics)
                    } else {
                        callbacks.onInstrumentalChange(option == R.string.create_settings_instrumental)
                    }
                },
                compact = true,
            ) { stringResource(it) }
        }
        SettingsGroup(R.string.create_settings_engine) {
            StudioSegmentedControl(Quality.entries, draft.quality, callbacks.onQualitySelect) {
                stringResource(it.label)
            }
        }
        StudioChip(
            stringResource(R.string.create_chip_styles),
            { callbacks.onComingSoon(ComingSoonFeature.Styles) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            StudioButton(
                text = stringResource(R.string.create_settings_reset),
                onClick = callbacks.onReset,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Secondary,
            )
            StudioButton(
                text = stringResource(R.string.create_settings_apply),
                onClick = callbacks.onApply,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A label over one setting. */
@Composable
private fun SettingsGroup(@StringRes label: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(StudioTheme.colors.surface.level2, StudioTheme.shapes.xl)
            .padding(StudioTheme.dimens.layout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs),
    ) {
        StudioText(
            stringResource(label),
            StudioTheme.typography.labelLarge,
            color = StudioTheme.colors.content.secondary,
        )
        content()
    }
}

@PreviewLightDark
@Composable
private fun SettingsContentPreview() {
    PreviewScreen {
        SettingsContent(
            draft = CreatePreviewData.settingsDraft,
            callbacks = SettingsCallbacks({}, {}, {}, {}, {}, {}),
            onDismissRequest = {},
            modifier = Modifier.padding(StudioTheme.dimens.spacing.m),
        )
    }
}
