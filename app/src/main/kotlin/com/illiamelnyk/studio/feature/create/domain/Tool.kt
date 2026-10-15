package com.illiamelnyk.studio.feature.create.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Audio of the user's own that a tool works on, which the app can't record or upload yet. */
enum class Recording {
    /** The user singing or humming. */
    Voice,

    /** A short loop or sound. */
    Sample,
}

/**
 * A tool of Create: a ready-made prompt for a common idea, with slots to fill in, generated with
 * the public Music API.
 *
 * @param recording what the tool needs from the user besides the prompt, if anything.
 * @param defaultDuration one of [TrackLengths.all].
 */
enum class Tool(val recording: Recording?, val defaultDuration: Duration, val template: ToolTemplate) {
    VoiceToSong(
        recording = Recording.Voice,
        defaultDuration = 90.seconds,
        template = ToolTemplate.of(
            "The reference is the user singing, likely rough, off-pitch or recorded on a phone. Deliver a " +
                "faithful but dramatically improved, studio-quality version of this exact performance, with " +
                "{style} production. Keep the melody, the phrasing and the character of the voice; fix pitch " +
                "and timing, add a full arrangement and a balanced mix, and keep the heart of it. " +
                "RECORDING USE: set conditioning_ref to the recording at condition_strength high; " +
                "use source_from for the original vocal performance.",
            ToolSlot(
                id = "style",
                label = "Production style",
                options = listOf("Pop", "Indie", "R&B", "Acoustic", "Electronic", "Rock", "Lo-fi"),
            ),
        ),
    ),
    Genreshift(
        recording = Recording.Voice,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "Take an existing {from} song and completely transform it into {to}. Keep the melody, the " +
                "mood and the emotional arc, but rebuild the rhythm, the instruments and the production so " +
                "it sounds native to the new genre, with a strong hook in the chorus. " +
                "RECORDING USE: use source_from for the melody and conditioning_ref at condition_strength medium.",
            ToolSlot(
                id = "from",
                label = "Original genre",
                options = listOf("Piano", "Indie rock", "Pop", "Folk", "Hip hop", "Jazz"),
            ),
            ToolSlot(
                id = "to",
                label = "New genre",
                options = listOf("Drum and bass", "Synthwave", "Bossa nova", "Orchestral", "Afrobeats", "Punk"),
            ),
        ),
    ),
    SampleToSong(
        recording = Recording.Voice,
        defaultDuration = 90.seconds,
        template = ToolTemplate.of(
            "Treat the reference as a sample to flip. RECORDING USE: use source_from to drop ONE short " +
                "recognizable snippet (a hook or a few bars) from the reference as a recurring motif. " +
                "Then build a new song around it with fresh lyrics and production, and use conditioning_ref " +
                "at condition_strength medium on nearby sections so the new material blends with the sampled snippet.",
        ),
    ),
    Unplugged(
        recording = Recording.Voice,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "An unplugged, acoustic take on {genre}: {instrument} and soft, close vocals, recorded live " +
                "in a small room. Strip the production down to what carries the melody and keep it " +
                "intimate and warm. RECORDING USE: preserve the melody with conditioning_ref at " +
                "condition_strength medium, and use source_from for the vocal reference.",
            ToolSlot(
                id = "genre",
                label = "Genre",
                options = listOf("Pop", "Indie rock", "R&B", "Hip hop", "Folk", "Electronic"),
            ),
            ToolSlot(
                id = "instrument",
                label = "Instrument",
                options = listOf("Acoustic guitar", "Piano", "Strings", "Ukulele", "Guitar and cello"),
            ),
        ),
    ),
    VoiceMemoToSong(
        recording = Recording.Voice,
        defaultDuration = 3.minutes,
        template = ToolTemplate.of(
            "Turn the user's voice memo into a complete song. Preserve the idea and emotional character, " +
                "write a coherent verse and chorus, and build a finished arrangement. RECORDING USE: use " +
                "source_from for the opening idea and conditioning_ref at condition_strength low for the new sections.",
        ),
    ),
    HumToSong(
        recording = Recording.Voice,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "The reference is the user's rough hummed or sung melody - treat it as the SEED of the song, " +
                "not the finished audio. Build a complete, professionally produced {style} song around it. " +
                "RECORDING USE: start the first section with source_from, a hard copy of the audio so the model " +
                "understands the vibe hummed. Then layer on instruments until full song. Set conditioning_ref " +
                "to the user's recording at condition_strength low (or as low as possible) - keep their melodic " +
                "contour and phrasing but turn it into real, in-tune sung vocals with full arrangement " +
                "(drums, bass, harmony). The hum has no usable words, so write fresh, singable lyrics that fit " +
                "the melody. It should sound like a finished record that grew from the user's idea.",
            ToolSlot("style", "Production style", listOf("Electronic", "Pop", "Acoustic", "Rock")),
        ),
    ),
    ;

    val featured: Boolean get() = this != VoiceMemoToSong && this != HumToSong

    init {
        require(defaultDuration in TrackLengths.all) { "$name starts at $defaultDuration, not a track length" }
    }
}
