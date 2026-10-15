package com.illiamelnyk.studio.feature.listen.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.Carousel
import com.illiamelnyk.studio.core.ui.components.StudioLinearProgress
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun CatalogSkeletonRow(modifier: Modifier = Modifier) {
    Carousel(listOf(0, 1, 2), modifier = modifier, key = { it }) {
        Column(verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
            Box(
                Modifier.size(
                    StudioTheme.dimens.size.cover.l,
                ).background(StudioTheme.colors.surface.level3, StudioTheme.shapes.m),
            )
            StudioLinearProgress(
                Modifier.size(
                    width = StudioTheme.dimens.size.cover.l,
                    height = StudioTheme.dimens.size.component.seekTrack,
                ),
            )
        }
    }
}
