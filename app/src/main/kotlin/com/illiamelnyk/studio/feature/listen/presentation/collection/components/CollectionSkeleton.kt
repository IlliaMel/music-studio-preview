package com.illiamelnyk.studio.feature.listen.presentation.collection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.components.SkeletonTextLine
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun CollectionSkeleton(modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    val coverSize = dimens.size.cover.l + dimens.spacing.giant
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(
                top = dimens.spacing.xl,
                start = dimens.spacing.m,
                end = dimens.spacing.m,
                bottom = dimens.spacing.m,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(coverSize).skeleton(true, StudioTheme.shapes.m))
            SkeletonTextLine(
                StudioTheme.typography.titleLarge,
                Modifier.padding(top = dimens.spacing.xs).width(coverSize),
            )
            SkeletonTextLine(StudioTheme.typography.bodySmall, Modifier.width(dimens.size.cover.l))
            Spacer(Modifier.height(dimens.spacing.xs))
        }
        Row(
            Modifier.fillMaxWidth().padding(dimens.spacing.s),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs, Alignment.CenterHorizontally),
        ) {
            repeat(2) {
                Box(
                    Modifier.size(dimens.size.cover.l, dimens.size.touchTarget)
                        .skeleton(true, StudioTheme.shapes.full),
                )
            }
        }
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().height(dimens.size.component.trackRow)
                    .padding(horizontal = dimens.layout.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(dimens.size.cover.s).skeleton(true, StudioTheme.shapes.m))
                Column(Modifier.weight(1f)) {
                    SkeletonTextLine(StudioTheme.typography.titleSmall, Modifier.fillMaxWidth(0.7f))
                    SkeletonTextLine(StudioTheme.typography.bodySmall, Modifier.fillMaxWidth(0.45f))
                }
            }
        }
    }
}
