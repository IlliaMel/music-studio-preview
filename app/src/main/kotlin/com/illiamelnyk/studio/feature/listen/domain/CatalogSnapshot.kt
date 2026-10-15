package com.illiamelnyk.studio.feature.listen.domain

import kotlin.time.Instant

data class TrendEntry(val trackId: String, val rank: Int, val playCount: Long)

data class TrendSnapshot(
    val id: String,
    val version: Int,
    val provider: String,
    val windowStart: Instant,
    val windowEnd: Instant,
    val entries: List<TrendEntry>,
)

data class CatalogSnapshot(
    val schemaVersion: Int,
    val version: String,
    val provider: String,
    val fictional: Boolean,
    val notice: String,
    val collections: List<CatalogCollection>,
    val trends: List<TrendSnapshot>,
) {
    fun collection(id: String): CatalogCollection? = collections.firstOrNull { it.id == id }
        ?: collections.firstOrNull {
            it.id == when (id) {
                "trending" -> "top-0"
                "new" -> "new-0"
                "vocals" -> "madeonstudio-1"
                "focus" -> "genres-0"
                else -> ""
            }
        }
}
