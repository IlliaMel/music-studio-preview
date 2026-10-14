package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.DurationPicker
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.CreatePreviewData
import com.illiamelnyk.studio.feature.create.presentation.CreateState
import com.illiamelnyk.studio.feature.create.presentation.ModelUi
import com.illiamelnyk.studio.feature.create.presentation.SettingsUi
import com.illiamelnyk.studio.feature.create.presentation.StyleUi
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.OverlaySheet
import com.illiamelnyk.studio.feature.create.presentation.common.rememberLatest
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList

/** The callbacks of the settings sheet, one per choice. */
@Stable
class SettingsCallbacks(
    val onModelSelect: (MusicModel) -> Unit,
    val onInstrumentalChange: (Boolean) -> Unit,
    val onDurationSelect: (Duration) -> Unit,
    val onStyleSelect: (String?) -> Unit,
    val onReset: () -> Unit,
    val onApply: () -> Unit,
)

/**
 * The composer's settings, open while [draft] isn't `null`: the model, vocals or not, the length
 * and the style. Choices apply to the composer only with Apply; Reset brings back the defaults.
 */
@Composable
fun SettingsSheet(
    draft: SettingsUi?,
    models: ImmutableList<ModelUi>,
    durations: ImmutableList<DurationOptionUi>,
    styles: ImmutableList<StyleUi>?,
    callbacks: SettingsCallbacks,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLatest(draft)
    OverlaySheet(visible = draft != null, onDismissRequest = onDismissRequest, modifier = modifier) {
        if (shown != null) SettingsContent(shown, models, durations, styles, callbacks)
    }
}

@Composable
private fun SettingsContent(
    draft: SettingsUi,
    models: ImmutableList<ModelUi>,
    durations: ImmutableList<DurationOptionUi>,
    styles: ImmutableList<StyleUi>?,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.l),
    ) {
        StudioText(stringResource(R.string.create_settings_title), StudioTheme.typography.titleMedium)
        SettingsGroup(R.string.create_settings_model) {
            ModelCards(models, selected = draft.model, onSelect = callbacks.onModelSelect)
        }
        SettingsGroup(R.string.create_settings_vocals) {
            StudioSegmentedControl(
                options = listOf(false, true),
                selected = draft.instrumental,
                onSelect = callbacks.onInstrumentalChange,
            ) { instrumental ->
                stringResource(
                    if (instrumental) R.string.create_settings_instrumental else R.string.create_settings_with_vocals,
                )
            }
        }
        SettingsGroup(R.string.create_settings_length) {
            DurationPicker(durations, selected = draft.duration, onSelect = {
                callbacks.onDurationSelect(it.duration)
            }) {
                it.label
            }
        }
        SettingsGroup(R.string.create_settings_style) {
            StylePicker(styles, selectedId = draft.styleId, onSelect = callbacks.onStyleSelect)
        }
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
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
    val state = CreateState(styles = CreatePreviewData.styles)
    PreviewScreen {
        SettingsContent(
            draft = CreatePreviewData.settingsDraft,
            models = state.models,
            durations = state.durations,
            styles = state.styles,
            callbacks = SettingsCallbacks({}, {}, {}, {}, {}, {}),
            modifier = Modifier.padding(StudioTheme.dimens.spacing.m),
        )
    }
}
