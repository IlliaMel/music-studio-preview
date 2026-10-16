package com.illiamelnyk.studio.core.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.illiamelnyk.studio.MainActivity
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationPreferences
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationTarget
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONObject

class AndroidGenerationNotifications(
    private val context: Context,
    private val choice: GenerationNotificationPreferences,
) : GenerationNotifications {
    private val preferences = context.getSharedPreferences("generation-notifications", Context.MODE_PRIVATE)
    private val manager by lazy { requireNotNull(context.getSystemService(NotificationManager::class.java)) }
    private val lock = Any()

    override fun record(ownerId: String?, tracks: List<Track>) = recordSnapshot(ownerId, tracks, emptySet())

    override fun recover(ownerId: String?, tracks: List<Track>, observedIds: Set<String>) =
        recordSnapshot(ownerId, tracks, observedIds)

    private fun recordSnapshot(ownerId: String?, tracks: List<Track>, observedIds: Set<String>) = synchronized(lock) {
        val previous = preferences.getString(ACTIVE, null)
        if (previous != ownerId) {
            manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }
                .forEach { manager.cancel(it.tag, it.id) }
            commit(preferences.edit().putString(ACTIVE, ownerId))
        }
        if (ownerId == null) return@synchronized
        val ledger = ledger(ownerId)
        val ids = tracks.map { it.id.value }.toSet()
        ledger.keys().asSequence().filter { it !in ids }.toList().forEach { id ->
            manager.cancel(tag(ownerId, id), 1)
            ledger.remove(id)
        }
        for (track in tracks) {
            val id = track.id.value
            val generating = track.status == TrackStatus.Queued || track.status is TrackStatus.Generating
            val entry = ledger.optJSONObject(id)
            if (entry == null && !generating && id !in observedIds) continue
            val item = entry ?: JSONObject().put("delivered", false)
            if (generating && entry != null && entry.optString("status") in setOf("ready", "failed")) {
                item.put("delivered", false)
                manager.cancel(tag(ownerId, id), 1)
            }
            item.put("title", track.title)
            item.put(
                "status",
                when (track.status) {
                    TrackStatus.Ready -> "ready"
                    is TrackStatus.Failed -> "failed"
                    else -> "pending"
                },
            )
            ledger.put(id, item)
        }
        commit(preferences.edit().putString(key(ownerId), ledger.toString()))
    }

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun deliver(ownerId: String?) {
        if (ownerId == null) return
        val pending = hasPending(ownerId)
        if (!pending) return
        val enabled = choice.isEnabled()
        synchronized(lock) {
            if (preferences.getString(ACTIVE, null) != ownerId) return
            val ledger = ledger(ownerId)
            val permissionGranted = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val allowed = enabled && manager.areNotificationsEnabled() && permissionGranted
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.generation_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            for (id in ledger.keys()) {
                val entry = ledger.getJSONObject(id)
                val status = entry.getString("status")
                if (entry.optBoolean("delivered") || status == "pending") continue
                if (allowed) {
                    manager.notify(tag(ownerId, id), 1, notification(ownerId, id, entry))
                }
                entry.put("delivered", true)
                commit(preferences.edit().putString(key(ownerId), ledger.toString()))
            }
        }
    }

    override fun hasPending(ownerId: String): Boolean = synchronized(lock) {
        if (preferences.getString(ACTIVE, null) != ownerId) return@synchronized false
        val ledger = ledger(ownerId)
        ledger.keys().asSequence().any { id ->
            val entry = ledger.getJSONObject(id)
            !entry.optBoolean("delivered") && entry.getString("status") != "pending"
        }
    }

    override fun forget(ownerId: String) = synchronized(lock) {
        val prefix = PREFIX + digest(ownerId) + "/"
        manager.activeNotifications.filter { it.tag?.startsWith(prefix) == true }
            .forEach { manager.cancel(it.tag, it.id) }
        val edit = preferences.edit().remove(key(ownerId))
        if (preferences.getString(ACTIVE, null) == ownerId) edit.remove(ACTIVE)
        commit(edit)
    }

    private fun notification(owner: String, id: String, entry: JSONObject): Notification {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(GenerationNotificationTarget.ACTION)
            .setData(Uri.Builder().scheme("studio").authority("generation").appendPath(owner).appendPath(id).build())
            .putExtra(GenerationNotificationTarget.OWNER, owner)
            .putExtra(GenerationNotificationTarget.TRACK, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (entry.getString("status") == "ready") {
            R.string.generation_notification_ready
        } else {
            R.string.generation_notification_failed
        }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(context.getString(title))
            .setContentText(entry.getString("title"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    private fun ledger(owner: String): JSONObject = JSONObject(preferences.getString(key(owner), null) ?: "{}")
    private fun key(owner: String) = PREFIX + digest(owner)
    private fun tag(owner: String, id: String) = key(owner) + "/" + id
    private fun commit(edit: android.content.SharedPreferences.Editor) {
        if (!edit.commit()) throw IOException("Cannot persist generation notification state")
    }
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val ACTIVE = "active-owner"
        const val PREFIX = "generation-"
        const val CHANNEL = "generation-completion"
    }
}
