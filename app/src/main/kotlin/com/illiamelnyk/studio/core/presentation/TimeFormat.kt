package com.illiamelnyk.studio.core.presentation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A track length or position as a clock shows it: `0:42`, `3:05`, `6:00:39`. */
fun Duration.toClockText(): String = toComponents { hours, minutes, seconds, _ ->
    val secondsText = seconds.toString().padStart(2, '0')
    if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secondsText" else "$minutes:$secondsText"
}

/** [toClockText] for a position in milliseconds, as the player reports it. */
fun Long.millisToClockText(): String = milliseconds.toClockText()
