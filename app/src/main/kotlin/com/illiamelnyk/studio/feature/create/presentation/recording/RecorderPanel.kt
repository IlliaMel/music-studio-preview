package com.illiamelnyk.studio.feature.create.presentation.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

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
        modifier.imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.s),
    ) {
        StudioText(stringResource(R.string.record_title), StudioTheme.typography.titleMedium)
        StudioText(stringResource(R.string.record_explanation), StudioTheme.typography.bodySmall)
        state.error?.let { StudioText(it.asString(), StudioTheme.typography.bodySmall, color = colors.error.content) }
        if (state.recording) {
            StudioText(
                pluralStringResource(
                    R.plurals.record_seconds,
                    (state.elapsedMs / 1000).toInt(),
                    (state.elapsedMs / 1000).toInt(),
                ),
                StudioTheme.typography.titleMedium,
            )
            Canvas(Modifier.fillMaxWidth().height(dimens.size.component.textField)) {
                repeat(40) { i ->
                    val x = size.width * (i + .5f) / 40
                    val half = size.height * (.1f + state.amplitude * ((i * 7 % 13) / 13f)) / 2
                    drawLine(
                        colors.content.primary,
                        Offset(x, size.height / 2 - half),
                        Offset(x, size.height / 2 + half),
                        strokeWidth =
                            dimens.stroke.hairline.toPx() * 3,
                    )
                }
            }
            StudioButton(
                stringResource(if (state.paused) R.string.record_resume else R.string.record_pause),
                onPauseResume,
            )
            StudioButton(stringResource(R.string.record_stop), onFinish, enabled = state.elapsedMs >= 10_000)
        } else {
            StudioButton(stringResource(R.string.record_start), onRecord, enabled = !state.importing)
            StudioButton(
                stringResource(R.string.record_choose),
                onPick,
                style = StudioButtonStyle.Secondary,
                enabled = !state.importing,
            )
        }
        state.audio?.let { audio ->
            StudioTextField(audio.title, onTitleChange)
            StudioButton(stringResource(R.string.record_preview), onPreview, style = StudioButtonStyle.Text)
            StudioButton(stringResource(R.string.record_use), onUse, enabled = audio.title.isNotBlank())
            onSave?.let {
                StudioButton(
                    stringResource(R.string.record_save),
                    it,
                    style = StudioButtonStyle.Secondary,
                    enabled = audio.title.isNotBlank(),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun RecorderPanelPreview() {
    PreviewSurface { RecorderPanel(RecorderState(), {}, {}, {}, {}, {}, {}) }
}
