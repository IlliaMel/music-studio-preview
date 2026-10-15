package com.illiamelnyk.studio.feature.create.presentation

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.presentation.UiText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Until a backend batch id exists, two adjacent tracks with the same nonblank prompt made within
 * one minute on the same local day form a variation pair. Never combine three independent sends.
 */
fun List<Track>.toGroupedCreateUi(today: LocalDate, zone: ZoneId): ImmutableList<CreateTrackUi> {
    val sorted = sortedByDescending { it.createdAt }
    val days = sorted.map {
        java.time.Instant.ofEpochMilli(it.createdAt.toEpochMilliseconds()).atZone(zone).toLocalDate()
    }
    var pairedThrough = -1
    return sorted.mapIndexed { index, track ->
        val day = days[index]
        val dayLabel = if (index == 0 || days[index - 1] != day) {
            when (day) {
                today -> UiText.Resource(R.string.create_today)
                today.minusDays(1) -> UiText.Resource(R.string.create_yesterday)
                else -> UiText.Raw(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
        } else {
            null
        }
        val next = sorted.getOrNull(index + 1)
        val first = index > pairedThrough && next != null && track.prompt.isNotBlank() &&
            track.prompt == next.prompt && day == days[index + 1] &&
            track.createdAt - next.createdAt <= 1.minutes
        if (first) pairedThrough = index + 1
        val variation = when {
            first -> 1
            index == pairedThrough -> 2
            else -> null
        }
        track.toCreateUi().copy(dayLabel = dayLabel, variation = variation)
    }.toImmutableList()
}
