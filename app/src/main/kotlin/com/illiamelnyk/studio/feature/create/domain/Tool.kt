package com.illiamelnyk.studio.feature.create.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Audio supplied by the user for a tool. */
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
                "and timing, add a full arrangement and a balanced mix, and keep the heart of it.",
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
        recording = Recording.Voice,
        defaultDuration = 90.seconds,
        template = ToolTemplate.of(
            "Build a new song around a short, recognizable hook from the reference. Repeat the hook as a " +
                "motif, add fresh lyrics and production, and blend the new sections with the original sample.",
        ),
    ),
    Unplugged(
        recording = Recording.Voice,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "An unplugged, acoustic take on {genre}: {instrument} and soft, close vocals, recorded live " +
                "in a small room. Strip the production down to what carries the melody and keep it " +
                "intimate and warm, preserving the melody and character of the original vocals.",
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
                "write a coherent verse and chorus, and build a finished arrangement around the opening idea.",
        ),
    ),
    HumToSong(
        recording = Recording.Voice,
        defaultDuration = 2.minutes,
        template = ToolTemplate.of(
            "Build a complete {style} song around the melody in the user’s humming. Keep its melodic " +
                "contour and phrasing, add instruments and in-tune vocals, and write fresh, singable lyrics " +
                "that fit the melody.",
            ToolSlot("style", "Production style", listOf("Electronic", "Pop", "Acoustic", "Rock")),
        ),
    ),
    ;

    val featured: Boolean get() = this != VoiceMemoToSong && this != HumToSong

    init {
        require(defaultDuration in TrackLengths.all) { "$name starts at $defaultDuration, not a track length" }
    }
}
