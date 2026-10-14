package com.illiamelnyk.studio.feature.listen.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toClockText
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection

/** A collection as a row or a header shows it: "Trending Now", "6 tracks · 19:20". */
@Immutable
data class CollectionUi(val id: String, val title: String, val subtitle: UiText, val coverSeed: String)

fun CatalogCollection.toUi(): CollectionUi = CollectionUi(
    id = id,
    title = title,
    subtitle = UiText.Plural(
        R.plurals.listen_collection_summary,
        tracks.size,
        tracks.size,
        totalDuration.toClockText(),
    ),
    coverSeed = coverSeed.value,
)
