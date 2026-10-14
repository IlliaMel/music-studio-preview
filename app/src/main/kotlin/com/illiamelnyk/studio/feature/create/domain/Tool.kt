package com.illiamelnyk.studio.feature.create.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
        defaultDuration = 1.minutes,
        template = ToolTemplate.of(
            "The reference is the user singing, likely rough, off-pitch or recorded on a phone. Deliver a " +
                "faithful but dramatically improved, studio-quality version of this exact performance, with " +
                "{style} production. Keep the melody, the phrasing and the character of the voice; fix pitch " +
                "and timing, add a full arrangement and a balanced mix, and keep the heart of it.",
            ToolSlot(
                id = "style",
                label = "Production style",
                options = listOf("Pop", "Indie", "R&B", "Acoustic", "Electronic", "Rock", "Lo-fi"),
            ),
        ),
    ),
    Genreshift(
        recording = null,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "Take an existing {from} song and completely transform it into {to}. Keep the melody, the " +
                "mood and the emotional arc, but rebuild the rhythm, the instruments and the production so " +
                "it sounds native to the new genre, with a strong hook in the chorus.",
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
        recording = Recording.Sample,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "Build a complete {genre} track around the user's sample. Loop it as the main hook, then add " +
                "drums, bass and a {mood} arrangement that grows from a sparse intro to a full chorus and back.",
            ToolSlot(
                id = "genre",
                label = "Genre",
                options = listOf("Hip hop", "House", "Lo-fi", "Trap", "Funk", "Ambient"),
            ),
            ToolSlot(
                id = "mood",
                label = "Mood",
                options = listOf("Warm", "Dark", "Euphoric", "Melancholic", "Playful"),
            ),
        ),
    ),
    Unplugged(
        recording = null,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "An unplugged, acoustic take on {genre}: {instrument} and soft, close vocals, recorded live " +
                "in a small room. Strip the production down to what carries the melody and keep it " +
                "intimate and warm.",
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
    ;

    init {
        require(defaultDuration in TrackLengths.all) { "$name starts at $defaultDuration, not a track length" }
    }
}
