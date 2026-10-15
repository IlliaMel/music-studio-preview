package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.presentation.toClockText
import com.illiamelnyk.studio.feature.create.domain.TrackLengths
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A track length as a duration chip shows it: `1:30`. */
@Immutable
data class DurationOptionUi(val duration: Duration, val label: String)

fun Duration.toOptionUi(): DurationOptionUi = DurationOptionUi(this, toClockText())

/** Every track length, for a duration picker. */
val trackLengthOptions: ImmutableList<DurationOptionUi> = TrackLengths.all.map(Duration::toOptionUi).toImmutableList()
