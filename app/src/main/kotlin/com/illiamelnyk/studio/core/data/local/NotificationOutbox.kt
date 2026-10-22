package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.local.database.NotificationOutboxEntity
import java.util.concurrent.atomic.AtomicReference

/** Durable ledger plus an immutable read cache for nonblocking work scheduling decisions. */
internal class NotificationOutbox(private val store: StudioStore) {
    private val entries = AtomicReference<Map<String, NotificationOutboxEntity>>(emptyMap())
    suspend fun load() {
        entries.set(store.outboxes().associateBy { it.ownerId })
    }
    fun accept(value: NotificationOutboxEntity) {
        entries.updateAndGet { it + (value.ownerId to value) }
    }
    fun observed(owner: String): Set<String> = entries.get()[owner]?.observedIds.orEmpty().toSet()
    fun isDirty(owner: String): Boolean = entries.get()[owner]?.dirty == true
    suspend fun markDirty(owner: String) = accept(store.setOutboxDirty(owner, true))
    suspend fun recorded(owner: String) = accept(store.setOutboxDirty(owner, false))
}
