package com.illiamelnyk.studio.feature.create.presentation.recording

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.millisToClockText
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.RecordedAudio

@Composable
fun RecorderPanel(
    state: RecorderState,
    onRecord: () -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    onPick: () -> Unit,
    onPreview: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null,
    onTitleChange: (String) -> Unit = {},
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    Column(
        modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
    ) {
        StudioText(stringResource(R.string.record_title), StudioTheme.typography.titleMedium)
        StudioText(
            stringResource(
                if (state.recording || state.audio != null) {
                    R.string.record_idea_explanation
                } else {
                    R.string.record_explanation
                },
            ),
            StudioTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = dimens.spacing.s),
        )
        state.error?.let {
            StudioText(
                it.asString(),
                StudioTheme.typography.bodySmall,
                color = colors.error.content,
                textAlign = TextAlign.Center,
            )
        }
        when {
            state.recording -> RecordingControls(state, onPauseResume, onFinish)

            state.audio != null -> RecordingPreview(
                state.audio,
                onPreview,
                onTitleChange,
                onUse,
                onSave,
                onRecord,
                onPick,
                state.importing,
            )

            else -> {
                StudioButton(
                    stringResource(R.string.record_start),
                    onRecord,
                    Modifier.fillMaxWidth(),
                    size = StudioButtonSize.Large,
                    icon = StudioIcons.Mic,
                    enabled = !state.importing,
                )
                StudioButton(
                    stringResource(R.string.record_choose),
                    onPick,
                    Modifier.fillMaxWidth(),
                    style = StudioButtonStyle.Secondary,
                    size = StudioButtonSize.Large,
                    enabled = !state.importing,
                    loading = state.importing,
                )
            }
        }
    }
}

@Composable
private fun RecordingControls(state: RecorderState, onPauseResume: () -> Unit, onFinish: () -> Unit) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val pauseLabel = stringResource(if (state.paused) R.string.record_resume else R.string.record_pause)
    val stopEnabled = state.elapsedMs >= MINIMUM_RECORDING_MS
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
    ) {
        StudioText(
            state.elapsedMs.millisToClockText(),
            StudioTheme.typography.bodyMedium,
            color = colors.content.secondary,
        )
        Spacer(Modifier.height(dimens.spacing.xxl))
        RecordingWaveform(state.amplitude, Modifier.fillMaxWidth(.75f), muted = state.paused)
        Spacer(Modifier.height(dimens.spacing.giant))
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.m), verticalAlignment = Alignment.Top) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            ) {
                RecordingControl(
                    icon = if (state.paused) StudioIcons.PlayArrowFilled else StudioIcons.PauseFilled,
                    description = pauseLabel,
                    onClick = onPauseResume,
                )
                StudioText(pauseLabel, StudioTheme.typography.bodySmall, color = colors.content.secondary)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            ) {
                RecordingControl(
                    icon = StudioIcons.StopFilled,
                    description = stringResource(R.string.record_stop),
                    onClick = onFinish,
                    enabled = stopEnabled,
                    tint = colors.error.content,
                )
                StudioText(
                    stringResource(R.string.record_stop),
                    StudioTheme.typography.bodySmall,
                    color = if (stopEnabled) colors.content.secondary else colors.content.disabled,
                )
            }
        }
        if (!stopEnabled) {
            val remaining = ((MINIMUM_RECORDING_MS - state.elapsedMs + 999) / 1000).toInt()
            StudioText(
                stringResource(R.string.record_minimum_remaining, remaining),
                StudioTheme.typography.bodySmall,
                color = colors.content.secondary,
            )
        }
    }
}

@Composable
private fun RecordingPreview(
    audio: RecordedAudio,
    onPreview: () -> Unit,
    onTitleChange: (String) -> Unit,
    onUse: () -> Unit,
    onSave: (() -> Unit)?,
    onRecord: () -> Unit,
    onPick: () -> Unit,
    importing: Boolean,
) {
    val dimens = StudioTheme.dimens
    val colors = StudioTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
    ) {
        StudioText(
            stringResource(R.string.record_ready, audio.durationMs.millisToClockText()),
            StudioTheme.typography.bodyMedium,
            color = colors.content.secondary,
        )
        Spacer(Modifier.height(dimens.spacing.xxl))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        ) {
            RecordingControl(
                StudioIcons.PlayArrowFilled,
                stringResource(R.string.record_preview),
                onPreview,
                compact = true,
            )
            RecordingWaveform(.35f, Modifier.weight(1f), muted = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StudioText(0L.millisToClockText(), StudioTheme.typography.bodySmall, color = colors.content.secondary)
            StudioText(
                audio.durationMs.millisToClockText(),
                StudioTheme.typography.bodySmall,
                color = colors.content.secondary,
            )
        }
        Spacer(Modifier.height(dimens.spacing.m))
        StudioText(
            stringResource(R.string.record_name),
            StudioTheme.typography.bodyMedium,
            Modifier.fillMaxWidth(),
        )
        StudioTextField(audio.title, onTitleChange, Modifier.fillMaxWidth())
        StudioButton(
            stringResource(R.string.record_use),
            onUse,
            Modifier.fillMaxWidth(),
            size = StudioButtonSize.Large,
            enabled = audio.title.isNotBlank() && !importing,
        )
        onSave?.let {
            StudioButton(
                stringResource(R.string.record_save),
                it,
                Modifier.fillMaxWidth(),
                style = StudioButtonStyle.Secondary,
                size = StudioButtonSize.Large,
                enabled = audio.title.isNotBlank() && !importing,
            )
        }
        StudioButton(
            stringResource(R.string.record_again),
            onRecord,
            Modifier.fillMaxWidth(),
            style = StudioButtonStyle.Text,
            enabled = !importing,
        )
        StudioButton(
            stringResource(R.string.record_choose),
            onPick,
            Modifier.fillMaxWidth(),
            style = StudioButtonStyle.Text,
            enabled = !importing,
            loading = importing,
        )
    }
}

@Composable
private fun RecordingControl(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = StudioTheme.colors.content.primary,
    compact: Boolean = false,
) {
    val dimens = StudioTheme.dimens
    Box(
        modifier.minimumTouchTarget()
            .size(if (compact) dimens.size.touchTarget else dimens.size.component.playButton)
            .background(StudioTheme.colors.surface.level3, StudioTheme.shapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(
            icon,
            contentDescription = null,
            tint = if (enabled) tint else StudioTheme.colors.content.disabled,
            size = if (icon == StudioIcons.StopFilled) dimens.size.component.iconButton else dimens.size.icon.l,
        )
    }
}

@Composable
private fun RecordingWaveform(amplitude: Float, modifier: Modifier = Modifier, muted: Boolean = false) {
    val dimens = StudioTheme.dimens
    val color = if (muted) StudioTheme.colors.content.secondary else StudioTheme.colors.content.primary
    Canvas(modifier.height(dimens.size.control.m)) {
        repeat(WAVEFORM_BAR_COUNT) { index ->
            val x = size.width * (index + .5f) / WAVEFORM_BAR_COUNT
            val half = size.height * (.22f + amplitude.coerceIn(0f, 1f) * ((index * 7 % 13) / 13f) * .78f) / 2
            drawLine(
                color,
                Offset(x, size.height / 2 - half),
                Offset(x, size.height / 2 + half),
                strokeWidth = dimens.stroke.hairline.toPx() * 3,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val MINIMUM_RECORDING_MS = 10_000L
private const val WAVEFORM_BAR_COUNT = 32

@PreviewLightDark
@Composable
private fun RecorderPanelPreview() {
    PreviewSurface {
        RecorderPanel(RecorderState(recording = true, elapsedMs = 6_000, amplitude = .5f), {}, {}, {}, {}, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun RecorderIdlePreview() {
    PreviewSurface { RecorderPanel(RecorderState(), {}, {}, {}, {}, {}, {}) }
}

@PreviewLightDark
@Composable
private fun RecorderPausedPreview() {
    PreviewSurface {
        RecorderPanel(RecorderState(recording = true, paused = true, elapsedMs = 12_000), {}, {}, {}, {}, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun RecorderReadyPreview() {
    PreviewSurface {
        RecorderPanel(
            RecorderState(audio = RecordedAudio("file:///preview.m4a", "Idea Oct 15, 21:16", 11_000)),
            {},
            {},
            {},
            {},
            {},
            {},
            onSave = {},
        )
    }
}
