package com.illiamelnyk.studio.feature.create.domain

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.Style

/** The styles a prompt can be combined with, such as "Lo-fi" or "Synthwave". */
interface StyleCatalog {
    /** Every style, in the order to offer them. */
    suspend fun styles(): Outcome<List<Style>>
}
