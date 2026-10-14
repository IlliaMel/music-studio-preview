package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A daily limit of [DAILY_LIMIT] generations that [FakeTrackRepository] spends, the way the server
 * does. There is no daily reset: the app process doesn't live that long during a demo.
 *
 * Temporary: replaced by the backend-backed implementation.
 */
class FakeCreditsRepository : CreditsRepository {

    private val state = MutableStateFlow(Credits(remaining = DAILY_LIMIT, dailyLimit = DAILY_LIMIT))

    override val credits: Flow<Credits> = state.asStateFlow()

    /** Spends one credit; `false` when none is left. */
    fun trySpend(): Boolean {
        while (true) {
            val credits = state.value
            if (credits.remaining == 0) return false
            if (state.compareAndSet(credits, credits.copy(remaining = credits.remaining - 1))) return true
        }
    }

    companion object {
        const val DAILY_LIMIT = 8
    }
}
