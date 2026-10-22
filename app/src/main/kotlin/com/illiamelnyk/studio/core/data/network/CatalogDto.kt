package com.illiamelnyk.studio.core.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable(with = CatalogDtoSerializer::class)
internal data class CatalogDto(
    val schemaVersion: Int,
    val catalogVersion: String,
    val provenance: CatalogProvenanceDto,
    val tracks: List<TrackDto>,
    val collections: List<CatalogCollectionDto>,
    val stations: List<CatalogStationDto>,
    val trendSnapshots: List<CatalogTrendDto>,
)

@Serializable internal data class CatalogProvenanceDto(val provider: String, val fictional: Boolean, val notice: String)

@Serializable internal data class CatalogCollectionDto(
    val id: String,
    val title: String,
    val coverSeed: String,
    val trackIds: List<String>,
    val isStation: Boolean,
    val kind: String,
    val trendSnapshotId: String? = null,
)

@Serializable internal data class CatalogStationDto(val id: String, val live: Boolean, val trackIds: List<String>)

@Serializable internal data class CatalogTrendDto(
    val id: String,
    val version: Int,
    val provider: String,
    val window: CatalogWindowDto,
    val entries: List<CatalogTrendEntryDto>,
)

@Serializable internal data class CatalogWindowDto(val start: String, val end: String)

@Serializable internal data class CatalogTrendEntryDto(val trackId: String, val rank: Int, val playCount: Long)

@Serializable private data class CatalogPayload(
    val schemaVersion: Int,
    val catalogVersion: String,
    val provenance: CatalogProvenanceDto,
    val tracks: List<TrackDto>,
    val collections: List<CatalogCollectionDto>,
    val stations: List<CatalogStationDto>,
    val trendSnapshots: List<CatalogTrendDto>,
)

/** Reject personal/unknown root fields before the public document can enter a shared cache. */
internal object CatalogDtoSerializer : KSerializer<CatalogDto> {
    override val descriptor: SerialDescriptor = CatalogPayload.serializer().descriptor
    private val fields =
        setOf("schemaVersion", "catalogVersion", "provenance", "tracks", "collections", "stations", "trendSnapshots")
    override fun deserialize(decoder: Decoder): CatalogDto {
        val source = decoder as JsonDecoder
        val value = source.decodeJsonElement()
        require(value.jsonObject.keys.all { it in fields })
        value.jsonObject["tracks"]?.jsonArray?.forEach { track ->
            require("isLiked" !in track.jsonObject && "audioHeaders" !in track.jsonObject)
        }
        val payload = source.json.decodeFromJsonElement(CatalogPayload.serializer(), value)
        return CatalogDto(
            payload.schemaVersion,
            payload.catalogVersion,
            payload.provenance,
            payload.tracks,
            payload.collections,
            payload.stations,
            payload.trendSnapshots,
        )
    }
    override fun serialize(encoder: Encoder, value: CatalogDto) {
        val target = encoder as JsonEncoder
        target.encodeJsonElement(
            target.json.encodeToJsonElement(
                CatalogPayload.serializer(),
                CatalogPayload(
                    value.schemaVersion,
                    value.catalogVersion,
                    value.provenance,
                    value.tracks,
                    value.collections,
                    value.stations,
                    value.trendSnapshots,
                ),
            ),
        )
    }
}
