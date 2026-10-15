package com.illiamelnyk.studio.core.data.local

internal fun audioContentType(header: ByteArray, fallback: String?): String {
    val signature = header.take(12).toByteArray().toString(Charsets.ISO_8859_1)
    return when {
        signature.startsWith("RIFF") && signature.substring(8).startsWith("WAVE") -> "audio/wav"

        signature.drop(4).startsWith("ftyp") -> "audio/mp4"

        signature.startsWith("OggS") -> "audio/ogg"

        signature.startsWith("fLaC") -> "audio/flac"

        signature.startsWith("#!AMR") -> "audio/amr"

        signature.startsWith("ID3") -> "audio/mpeg"

        header.size >= 2 && (header[0].toInt() and 255) == 255 &&
            (header[1].toInt() and 246) == 240 -> "audio/aac"

        header.size >= 2 && (header[0].toInt() and 255) == 255 &&
            (header[1].toInt() and 224) == 224 -> "audio/mpeg"

        else -> when (fallback?.substringBefore(';')?.lowercase()) {
            "audio/x-m4a", "video/mp4" -> "audio/mp4"
            "audio/mp3", "audio/x-mpeg" -> "audio/mpeg"
            "audio/x-wav", "audio/wave" -> "audio/wav"
            else -> fallback ?: "application/octet-stream"
        }
    }
}

internal fun audioExtension(type: String): String = when (type) {
    "audio/wav" -> "wav"
    "audio/mp4" -> "m4a"
    "audio/mpeg" -> "mp3"
    "audio/ogg" -> "ogg"
    "audio/flac" -> "flac"
    "audio/amr" -> "amr"
    "audio/aac" -> "aac"
    "audio/webm" -> "webm"
    else -> "audio"
}

internal fun audioTypeFromPath(path: String): String? = when (path.substringAfterLast('.').lowercase()) {
    "wav" -> "audio/wav"
    "m4a", "mp4" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    "flac" -> "audio/flac"
    "amr" -> "audio/amr"
    "aac" -> "audio/aac"
    "webm" -> "audio/webm"
    else -> null
}
