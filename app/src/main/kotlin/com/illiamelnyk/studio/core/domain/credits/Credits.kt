package com.illiamelnyk.studio.core.domain.credits

import kotlinx.coroutines.flow.Flow

/** How many tracks the user can still generate today, out of [dailyLimit]. */
data class Credits(val remaining: Int, val dailyLimit: Int) {
    init {
        require(remaining in 0..dailyLimit) { "Remaining credits must be between 0 and $dailyLimit, was $remaining" }
    }
}

/** The generation limit. The server spends credits; the app only shows them. */
interface CreditsRepository {
    /** The current credits; emits again after every generation and at the daily reset. */
    val credits: Flow<Credits>
}
