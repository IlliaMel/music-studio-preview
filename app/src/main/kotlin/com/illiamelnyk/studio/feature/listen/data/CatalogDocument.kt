package com.illiamelnyk.studio.feature.listen.data

import com.illiamelnyk.studio.core.data.local.toTrack
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import com.illiamelnyk.studio.feature.listen.domain.CatalogSnapshot
import com.illiamelnyk.studio.feature.listen.domain.TrendEntry
import com.illiamelnyk.studio.feature.listen.domain.TrendSnapshot
import java.net.URI
import kotlin.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal object CatalogDocument {
    fun parse(json: JSONObject, apiOrigin: String? = null): CatalogSnapshot {
        require(json.keys().asSequence().all { it in ROOT_FIELDS })
        require(json.getInt("schemaVersion") == 1)
        val version = json.getString("catalogVersion").also { require(it.isNotBlank() && it.length <= 100) }
        val provenance = json.getJSONObject("provenance")
        val provider = provenance.getString("provider").also { require(it.isNotBlank()) }
        if (provider == "fixture") require(provenance.getBoolean("fictional"))
        val tracks = objects(json.getJSONArray("tracks")).map { item ->
            val id = item.getString("id")
            require(ID.matches(id))
            require(item.getString("title").isNotBlank() && item.getString("artist").isNotBlank())
            require(item.getLong("durationMs") in 1..3_600_000)
            require(!item.has("isLiked") && !item.has("audioHeaders"))
            if (apiOrigin != null) {
                val uri = URI(item.getString("audioUrl"))
                val expected = URI(apiOrigin)
                require(uri.scheme == expected.scheme && uri.host == expected.host && uri.port == expected.port)
                require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
                require(uri.path == "/v1/catalog/tracks/" + id + "/audio")
            }
            item.toTrack().copy(isLiked = false, audioHeaders = emptyMap())
        }
        require(tracks.isNotEmpty() && tracks.size <= 1000)
        val byId = tracks.associateBy { it.id.value }
        require(byId.size == tracks.size)
        val trends = objects(json.getJSONArray("trendSnapshots")).map { trend ->
            val window = trend.getJSONObject("window")
            val start = Instant.parse(window.getString("start"))
            val end = Instant.parse(window.getString("end"))
            require(start < end)
            val entries = objects(trend.getJSONArray("entries")).map { entry ->
                TrendEntry(entry.getString("trackId"), entry.getInt("rank"), entry.getLong("playCount"))
            }
            require(entries.isNotEmpty() && entries.map { it.trackId }.toSet().size == entries.size)
            require(entries.map { it.rank } == (1..entries.size).toList())
            require(entries.all { it.trackId in byId && it.playCount >= 0 })
            TrendSnapshot(
                trend.getString("id"),
                trend.getInt("version"),
                trend.getString("provider"),
                start,
                end,
                entries,
            ).also { require(it.version >= 1 && it.id.isNotBlank() && it.provider == provider) }
        }
        require(trends.map { it.id }.toSet().size == trends.size)
        val collections = objects(json.getJSONArray("collections")).map { collection ->
            val id = collection.getString("id")
            require(ID.matches(id))
            val ids = strings(collection.getJSONArray("trackIds"))
            require(ids.isNotEmpty() && ids.toSet().size == ids.size && ids.all(byId::containsKey))
            val kind = CatalogSectionKind.entries.first { it.name.equals(collection.getString("kind"), true) }
            require(collection.getBoolean("isStation") == (kind == CatalogSectionKind.Stations))
            val snapshotId = collection.optString("trendSnapshotId").takeIf { it.isNotEmpty() }
            snapshotId?.let { reference ->
                val snapshot = trends.first { it.id == reference }
                require(snapshot.entries.map { it.trackId } == ids)
            }
            if (id == "top-0") require(ids.size >= 20 && snapshotId != null)
            CatalogCollection(
                id,
                collection.getString("title"),
                CoverSeed(collection.getString("coverSeed")),
                ids.map(byId::getValue),
                collection.getBoolean("isStation"),
                kind,
                snapshotId,
            )
        }
        require(collections.map { it.id }.toSet().size == collections.size)
        require(collections.any { it.id == "top-0" && it.tracks.size >= 20 && it.trendSnapshotId != null })
        require(CatalogSectionKind.entries.all { kind -> collections.any { it.kind == kind } })
        val stations = objects(json.getJSONArray("stations"))
        require(stations.map { it.getString("id") } == collections.filter { it.isStation }.map { it.id })
        stations.forEach { station ->
            val collection = collections.first { it.id == station.getString("id") }
            require(station.getBoolean("live"))
            require(strings(station.getJSONArray("trackIds")) == collection.tracks.map { it.id.value })
        }
        return CatalogSnapshot(
            1,
            version,
            provider,
            provenance.getBoolean("fictional"),
            provenance.getString("notice"),
            collections,
            trends,
        )
    }

    private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map(array::getJSONObject)
    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map(array::getString)
    private val ROOT_FIELDS = setOf(
        "schemaVersion",
        "catalogVersion",
        "provenance",
        "tracks",
        "collections",
        "stations",
        "trendSnapshots",
    )
    private val ID = Regex("[a-z0-9][a-z0-9-]{0,99}")
}
