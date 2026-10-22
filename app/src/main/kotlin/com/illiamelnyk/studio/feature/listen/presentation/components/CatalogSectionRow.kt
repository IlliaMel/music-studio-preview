package com.illiamelnyk.studio.feature.listen.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.Carousel
import com.illiamelnyk.studio.core.ui.components.SectionHeader
import com.illiamelnyk.studio.core.ui.components.StationCard
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.TrackCard
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import com.illiamelnyk.studio.feature.listen.presentation.CatalogSectionUi

@Composable
fun CatalogSectionRow(
    section: CatalogSectionUi,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = Modifier.padding(horizontal = StudioTheme.dimens.layout.screenPadding)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s)) {
        SectionHeader(
            stringResource(section.kind.titleResource()),
            modifier = padding.heightIn(min = StudioTheme.dimens.size.touchTarget),
            onTitleClick = if (!section.isLoading &&
                !section.failed &&
                section.kind != CatalogSectionKind.Stations &&
                section.collections.isNotEmpty()
            ) {
                { onOpen(section.collections.first().id) }
            } else {
                null
            },
        )
        when {
            section.isLoading -> CatalogSkeletonRow(section.kind)

            section.failed -> Column(padding) {
                StudioText(stringResource(R.string.listen_section_error), StudioTheme.typography.bodyMedium)
                StudioButton(stringResource(R.string.listen_retry), onClick = onRetry)
            }

            section.collections.isEmpty() -> StudioText(
                stringResource(R.string.listen_section_empty),
                StudioTheme.typography.bodyMedium,
                padding,
            )

            else -> Carousel(section.collections, key = { it.id }) { collection ->
                if (section.kind == CatalogSectionKind.Stations) {
                    StationCard(
                        collection.title,
                        stringResource(
                            R.string.listen_live,
                        ),
                        collection.coverSeed,
                        onClick = {
                            onOpen(collection.id)
                        },
                    )
                } else if (section.kind == CatalogSectionKind.Genres) {
                    GenreCard(collection, onClick = { onOpen(collection.id) })
                } else {
                    TrackCard(collection.title, collection.subtitle.asString(), collection.coverSeed, onClick = {
                        onOpen(collection.id)
                    })
                }
            }
        }
    }
}

private fun CatalogSectionKind.titleResource(): Int = when (this) {
    CatalogSectionKind.Stations -> R.string.listen_stations
    CatalogSectionKind.New -> R.string.listen_new
    CatalogSectionKind.Top -> R.string.listen_top
    CatalogSectionKind.Rising -> R.string.listen_rising
    CatalogSectionKind.Genres -> R.string.listen_genres
    CatalogSectionKind.MadeOnStudio -> R.string.listen_made
    CatalogSectionKind.Remixes -> R.string.listen_remixes
    CatalogSectionKind.Featured -> R.string.listen_featured
}
