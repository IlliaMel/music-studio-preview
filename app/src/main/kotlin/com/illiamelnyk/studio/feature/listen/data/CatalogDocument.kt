package com.illiamelnyk.studio.feature.listen.data

import com.illiamelnyk.studio.core.data.network.CatalogDto
import com.illiamelnyk.studio.core.data.network.toTrack
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import com.illiamelnyk.studio.feature.listen.domain.CatalogSnapshot
import com.illiamelnyk.studio.feature.listen.domain.TrendEntry
import com.illiamelnyk.studio.feature.listen.domain.TrendSnapshot
import java.net.URI
import kotlin.time.Instant
import kotlinx.serialization.json.Json

internal object CatalogDocument {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    fun decode(source: String): CatalogDto = json.decodeFromString(CatalogDto.serializer(), source)
    fun encode(document: CatalogDto): String = json.encodeToString(CatalogDto.serializer(), document)

    fun parse(document: CatalogDto, apiOrigin: String? = null): CatalogSnapshot {
        require(document.schemaVersion == 1)
        val version = document.catalogVersion.also { require(it.isNotBlank() && it.length <= 100) }
        val provider = document.provenance.provider.also { require(it.isNotBlank()) }
        if (provider == "fixture") require(document.provenance.fictional)
        val tracks = document.tracks.map { item ->
            require(ID.matches(item.id))
            require(!item.title.isNullOrBlank() && item.artist.isNotBlank())
            require((item.durationMs ?: 0) in 1..3_600_000)
            require(item.isLiked == null && item.audioHeaders == null)
            if (apiOrigin != null) {
                val uri = URI(requireNotNull(item.audioUrl))
                val expected = URI(apiOrigin)
                require(uri.scheme == expected.scheme && uri.host == expected.host && uri.port == expected.port)
                require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
                require(uri.path == "/v1/catalog/tracks/" + item.id + "/audio")
            }
            item.toTrack().copy(isLiked = false)
        }
        require(tracks.isNotEmpty() && tracks.size <= 1000)
        val byId = tracks.associateBy { it.id.value }
        require(byId.size == tracks.size)
        val trends = document.trendSnapshots.map { trend ->
            val start = Instant.parse(trend.window.start)
            val end = Instant.parse(trend.window.end)
            require(start < end)
            val entries = trend.entries.map { TrendEntry(it.trackId, it.rank, it.playCount) }
            require(entries.isNotEmpty() && entries.map { it.trackId }.toSet().size == entries.size)
            require(entries.map { it.rank } == (1..entries.size).toList())
            require(entries.all { it.trackId in byId && it.playCount >= 0 })
            TrendSnapshot(trend.id, trend.version, trend.provider, start, end, entries).also {
                require(it.version >= 1 && it.id.isNotBlank() && it.provider == provider)
            }
        }
        require(trends.map { it.id }.toSet().size == trends.size)
        val collections = document.collections.map { collection ->
            require(ID.matches(collection.id))
            val ids = collection.trackIds
            require(ids.isNotEmpty() && ids.toSet().size == ids.size && ids.all(byId::containsKey))
            val kind = CatalogSectionKind.entries.first { it.name.equals(collection.kind, true) }
            require(collection.isStation == (kind == CatalogSectionKind.Stations))
            val snapshotId = collection.trendSnapshotId?.takeIf { it.isNotEmpty() }
            snapshotId?.let { reference ->
                require(trends.first { it.id == reference }.entries.map { it.trackId } == ids)
            }
            if (collection.id == "top-0") require(ids.size >= 20 && snapshotId != null)
            CatalogCollection(
                collection.id,
                collection.title,
                CoverSeed(collection.coverSeed),
                ids.map(byId::getValue),
                collection.isStation,
                kind,
                snapshotId,
            )
        }
        require(collections.map { it.id }.toSet().size == collections.size)
        require(collections.any { it.id == "top-0" && it.tracks.size >= 20 && it.trendSnapshotId != null })
        require(CatalogSectionKind.entries.all { kind -> collections.any { it.kind == kind } })
        require(document.stations.map { it.id } == collections.filter { it.isStation }.map { it.id })
        document.stations.forEach { station ->
            val collection = collections.first { it.id == station.id }
            require(station.live)
            require(station.trackIds == collection.tracks.map { it.id.value })
        }
        return CatalogSnapshot(
            1,
            version,
            provider,
            document.provenance.fictional,
            document.provenance.notice,
            collections,
            trends,
        )
    }
    private val ID = Regex("[a-z0-9][a-z0-9-]{0,99}")
}
