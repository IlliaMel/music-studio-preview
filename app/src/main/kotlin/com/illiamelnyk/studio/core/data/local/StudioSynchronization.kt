package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.Outcome

/** Platform boundary for owner-fenced durable work and visible UI synchronization. */
interface StudioSynchronization {
    val activeAccountId: String?
    suspend fun syncPending(ownerId: String): Outcome<Boolean>
    suspend fun recoverPendingDeletions(): Outcome<Boolean>
    fun setForeground(active: Boolean)
}
