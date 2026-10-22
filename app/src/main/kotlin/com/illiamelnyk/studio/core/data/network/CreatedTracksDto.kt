package com.illiamelnyk.studio.core.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** The server accepts a batch while older deployments return a single accepted track. */
@Serializable(with = CreatedTracksSerializer::class)
internal data class CreatedTracksDto(val tracks: List<TrackDto>)

internal object CreatedTracksSerializer : KSerializer<CreatedTracksDto> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CreatedTracks") {
        element("tracks", ListSerializer(TrackDto.serializer()).descriptor)
    }
    override fun deserialize(decoder: Decoder): CreatedTracksDto {
        val source = decoder as JsonDecoder
        val value = source.decodeJsonElement()
        val tracks = value.jsonObject["tracks"]?.let {
            source.json.decodeFromJsonElement(ListSerializer(TrackDto.serializer()), it)
        } ?: listOf(source.json.decodeFromJsonElement(TrackDto.serializer(), value))
        require(tracks.size in 1..2)
        return CreatedTracksDto(tracks)
    }
    override fun serialize(encoder: Encoder, value: CreatedTracksDto) {
        val target = encoder as JsonEncoder
        target.encodeJsonElement(
            buildJsonObject {
                put("tracks", target.json.encodeToJsonElement(ListSerializer(TrackDto.serializer()), value.tracks))
            },
        )
    }
}
