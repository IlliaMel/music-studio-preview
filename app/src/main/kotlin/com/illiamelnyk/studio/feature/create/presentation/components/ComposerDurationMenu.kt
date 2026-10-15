package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.ContextMenuItem
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.components.StudioContextMenu
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.trackLengthOptions
import kotlin.time.Duration

/** A duration popup anchored to its chip; null lets the engine choose. */
@Composable
fun ComposerDurationMenu(selected: DurationOptionUi?, onSelect: (Duration?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        StudioChip(
            text = selected?.label ?: stringResource(R.string.create_quality_auto),
            onClick = { expanded = true },
            leadingIcon = StudioIcons.Schedule,
        )
        StudioContextMenu(expanded, { expanded = false }, Modifier.verticalScroll(rememberScrollState())) {
            (listOf(null) + trackLengthOptions).forEach { option ->
                ContextMenuItem(
                    label = option?.label ?: stringResource(R.string.create_quality_auto),
                    onClick = {
                        expanded = false
                        onSelect(option?.duration)
                    },
                    icon = if (selected == option) StudioIcons.CheckCircleFilled else null,
                )
            }
        }
    }
}
