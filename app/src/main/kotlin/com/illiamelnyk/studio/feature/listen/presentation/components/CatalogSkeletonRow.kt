package com.illiamelnyk.studio.feature.listen.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.Carousel
import com.illiamelnyk.studio.core.ui.components.SkeletonTextLine
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind

@Composable
fun CatalogSkeletonRow(kind: CatalogSectionKind, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Carousel(listOf(0, 1, 2), modifier = modifier, key = { it }) {
        if (kind == CatalogSectionKind.Stations) {
            Box(
                Modifier.size(dimens.size.component.stationWidth, dimens.size.component.stationHeight)
                    .skeleton(true, StudioTheme.shapes.l),
            )
        } else {
            Column(Modifier.width(dimens.size.cover.l), verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                Box(Modifier.size(dimens.size.cover.l).skeleton(true, StudioTheme.shapes.m))
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs)) {
                    SkeletonTextLine(StudioTheme.typography.titleSmall, Modifier.fillMaxWidth())
                    if (kind != CatalogSectionKind.Genres) {
                        SkeletonTextLine(StudioTheme.typography.bodySmall, Modifier.fillMaxWidth(0.65f))
                    }
                }
            }
        }
    }
}
