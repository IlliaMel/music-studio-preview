package com.illiamelnyk.studio.core.data.local

import android.net.Uri
import com.illiamelnyk.studio.core.domain.track.TrackId

internal fun trackResourcePath(id: TrackId): String = if (id.value.startsWith("recording-")) {
    "/v1/recordings/" + segment(id.value.removePrefix("recording-"))
} else {
    "/v1/tracks/" + segment(id.value)
}
internal fun StudioApi.trackAudioUri(id: TrackId): String = baseUrl + trackResourcePath(id) + "/audio"
internal fun StudioApi.catalogAudioUri(id: TrackId): String =
    baseUrl + "/v1/catalog/tracks/" + segment(id.value) + "/audio"
private fun segment(value: String): String = Uri.encode(value)
