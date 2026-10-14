package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.foundation.skeleton
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** Outlines of track rows while the list loads, as items of the screen's list. */
fun LazyListScope.trackSkeletons() {
    items(count = SKELETON_ROWS, key = { "skeleton$it" }, contentType = { "skeleton" }) { TrackRowSkeleton() }
}

/** The outline of a track row: the cover, the title and the subtitle. */
@Composable
fun TrackRowSkeleton(modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.trackRow)
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.size.cover.s).skeleton(visible = true, shape = StudioTheme.shapes.s))
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            Box(Modifier.fillMaxWidth(TITLE_WIDTH).height(dimens.size.icon.s).skeleton(visible = true))
            Box(Modifier.fillMaxWidth(SUBTITLE_WIDTH).height(dimens.spacing.s).skeleton(visible = true))
        }
    }
}

private const val SKELETON_ROWS = 4
private const val TITLE_WIDTH = 0.6f
private const val SUBTITLE_WIDTH = 0.35f

@PreviewLightDark
@Composable
private fun TrackRowSkeletonPreview() {
    PreviewScreen { TrackRowSkeleton() }
}
