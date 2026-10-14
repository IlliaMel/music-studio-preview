package com.illiamelnyk.studio.feature.create.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A tool of Create: a ready-made prompt for a common idea, generated with the public Music API.
 *
 * @param needsRecording the tool works on the user's own audio (a voice or a sample), which the
 *   app can't record yet.
 * @param promptTemplate what the tool asks the service for.
 */
enum class Tool(val needsRecording: Boolean, val defaultDuration: Duration, val promptTemplate: String) {
    VoiceToSong(
        needsRecording = true,
        defaultDuration = 1.minutes,
        promptTemplate = "Turn a hummed melody into a full pop song with drums, bass and a warm pad",
    ),
    Genreshift(
        needsRecording = false,
        defaultDuration = 2.minutes,
        promptTemplate = "Reimagine a calm piano ballad as an upbeat drum and bass track",
    ),
    SampleToSong(
        needsRecording = true,
        defaultDuration = 2.minutes,
        promptTemplate = "Build a full lo-fi hip hop track around a short vinyl sample",
    ),
    Unplugged(
        needsRecording = false,
        defaultDuration = 2.minutes,
        promptTemplate = "An unplugged acoustic version of an indie rock song, with guitar and soft vocals",
    ),
}
