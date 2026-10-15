package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.track.TrackStatus
import org.json.JSONArray
import org.json.JSONObject

internal class NotificationOutbox(private val store: StudioStore) {
    fun register(root: JSONObject, account: AccountSnapshot) {
        val owner = account.session.user.id.value
        val ids = account.tracks.map { it.id.value }.toSet()
        val observed = root.array(OBSERVED + owner).strings().filter { it in ids }.toSet() +
            account.tracks.filter { it.status == TrackStatus.Queued || it.status is TrackStatus.Generating }
                .map { it.id.value }
        root.put(OBSERVED + owner, JSONArray(observed.toList()))
        root.put(DIRTY + owner, true)
    }

    fun observed(owner: String): Set<String> = store.read().array(OBSERVED + owner).strings().toSet()
    fun isDirty(owner: String): Boolean = store.read().optBoolean(DIRTY + owner)
    fun markDirty(owner: String) = store.change { it.put(DIRTY + owner, true) }
    fun recorded(owner: String) = store.change { it.remove(DIRTY + owner) }

    companion object {
        private const val OBSERVED = "notification-observed-"
        private const val DIRTY = "notification-dirty-"
        fun remove(root: JSONObject, owner: String) {
            root.remove(OBSERVED + owner)
            root.remove(DIRTY + owner)
        }
    }
}
