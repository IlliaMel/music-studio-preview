package com.illiamelnyk.studio.core.domain.track

import com.illiamelnyk.studio.core.domain.CoverSeed
import kotlin.time.Duration

/** The music model a track is generated with. */
enum class MusicModel {
    V2_5,
    V2,
}

/** The trade-off between generation time and detail. */
enum class Quality {
    /** The service decides. */
    Auto,
    Deep,
    Fast,
}

/** A named style the prompt can be combined with, such as "Lo-fi" or "Synthwave". */
data class Style(val id: String, val name: String, val tags: List<String>, val coverSeed: CoverSeed)

/** What the user asks to generate. A null duration lets the service choose (Auto). */
data class GenerationRequest(
    val prompt: String,
    val duration: Duration? = null,
    val model: MusicModel = MusicModel.V2_5,
    val quality: Quality = Quality.Auto,
    val instrumental: Boolean = false,
    val style: Style? = null,
    val customLyrics: String? = null,
    val referenceUploadId: String? = null,
    val remixTrackId: String? = null,
    val referenceAudioUri: String? = null,
)
