package com.illiamelnyk.studio.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioBadgeStyle
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.navigation.ShellVariant
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Settings that exist only in debug builds, shown at the end of the profile. */
val debugSettings: (@Composable () -> Unit)? = { ShellVariantSetting() }

/**
 * Switches the shell between the two top-level layouts, to compare them on a device. The tabs are
 * rebuilt in the new layout, starting at Create.
 */
@Composable
private fun ShellVariantSetting(modifier: Modifier = Modifier, store: DebugShellVariantStore = koinInject()) {
    val variant by store.variant.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val dimens = StudioTheme.dimens
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacing.s)) {
        StudioBadge(stringResource(R.string.debug_settings), style = StudioBadgeStyle.Demo)
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.layout.inlineGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioText(
                stringResource(R.string.debug_shell_variant),
                StudioTheme.typography.titleMedium,
                Modifier.weight(1f),
            )
            variant?.let { selected ->
                StudioSegmentedControl(
                    options = ShellVariant.entries,
                    selected = selected,
                    onSelect = { scope.launch { store.set(it) } },
                ) { option ->
                    stringResource(
                        when (option) {
                            ShellVariant.TopSwitch -> R.string.debug_shell_top_switch
                            ShellVariant.BottomBar -> R.string.debug_shell_bottom_bar
                        },
                    )
                }
            }
        }
    }
}
