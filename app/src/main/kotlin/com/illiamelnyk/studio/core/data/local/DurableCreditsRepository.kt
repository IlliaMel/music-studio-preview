package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DurableCreditsRepository(private val runtime: StudioAccountRuntime) : CreditsRepository {
    override val credits: Flow<Credits> = runtime.state.map { it?.credits ?: Credits(0, 0) }.distinctUntilChanged()
    suspend fun refresh() {
        val account = runtime.requireAccount()
        val quota = runtime.api.getQuota(account.token ?: return)
        val limit = quota.limit
        runtime.save(account.copy(credits = Credits(quota.remaining.coerceIn(0, limit), limit), creditDay = utcDay()))
    }

    fun reset(account: AccountSnapshot): AccountSnapshot = if (account.token != null || account.creditDay == utcDay()) {
        account
    } else {
        account.copy(
            credits = Credits(
                25,
                25,
            ),
            creditDay = utcDay(),
        )
    }
}
