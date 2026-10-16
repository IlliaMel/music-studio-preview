package com.illiamelnyk.studio.core.data.playback

import android.annotation.SuppressLint
import android.content.SharedPreferences
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Writes are conflated per preference key; disk IO never runs on the caller or under its playback lock. */
internal class PlaybackStatePersistence(
    private val preferences: SharedPreferences,
    private val ownerId: () -> String?,
    private val writer: PlaybackPersistenceWriter = PlaybackPersistenceWriter.shared,
) {
    private val failure = AtomicReference<Throwable?>()
    private val identityLock = Any()
    private val flushLock = Any()

    @Volatile private var lease = Any()
    private class Removal(val owner: String, val generation: Long, val lease: Any)
    private val removals = mutableMapOf<String, Removal>()

    fun claim(owner: String) {
        synchronized(identityLock) {
            lease = Any()
            writer.claim(preferences, playbackPreferenceKey(owner), lease)
        }
    }

    fun owns(owner: String): Boolean = writer.owns(preferences, playbackPreferenceKey(owner), lease)

    fun read(key: String): String? = writer.read(preferences, key) as String?

    fun readValue(key: String): Any? = writer.read(preferences, key)

    fun contains(key: String): Boolean = writer.contains(preferences, key)

    fun pending(key: String): Boolean = writer.pending(preferences, key)

    fun write(key: String, value: String, owner: String, generation: Long, current: () -> Boolean) {
        val capturedLease = synchronized(identityLock) { lease }
        writer.enqueue(preferences, key, value, failure) {
            current() && writer.owns(preferences, playbackPreferenceKey(owner), capturedLease) &&
                ownerId() == owner && playbackStateGeneration(owner) == generation
        }
    }

    fun removeQueue(owner: String, generation: Long) {
        val removal = synchronized(identityLock) {
            Removal(owner, generation, lease).also { removals[owner] = it }
        }
        enqueueRemoval(removal)
    }

    private fun enqueueRemoval(removal: Removal) {
        writer.enqueue(preferences, playbackPreferenceKey(removal.owner), null, failure) {
            writer.owns(preferences, playbackPreferenceKey(removal.owner), removal.lease) &&
                playbackStateGeneration(removal.owner) == removal.generation
        }
    }

    fun removeUnclaimed(owner: String, generation: Long, observed: Any?) {
        val key = playbackPreferenceKey(owner)
        writer.enqueue(preferences, key, null, failure) {
            playbackStateGeneration(owner) == generation && writer.unclaimedAndUnchanged(preferences, key, observed)
        }
    }

    fun remove(keys: Collection<String>) {
        keys.forEach { writer.enqueue(preferences, it, null, failure) { true } }
    }

    /** Explicit durability boundary. Call only after releasing the playback lock. */
    fun flush() {
        synchronized(flushLock) {
            val pending = synchronized(identityLock) { removals.values.toList() }
            pending.forEach(::enqueueRemoval)
            writer.flush()
            failure.getAndSet(null)?.let { throw IOException("Cannot persist playback state", it) }
            synchronized(identityLock) {
                pending.forEach { if (removals[it.owner] === it) removals.remove(it.owner) }
            }
        }
    }
}

internal class PlaybackPersistenceWriter(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "studio-playback-storage").apply { isDaemon = true }
    },
) {
    private data class Key(val preferences: SharedPreferences, val name: String)

    private class Write(
        val key: Key,
        val value: String?,
        val failure: AtomicReference<Throwable?>,
        val current: () -> Boolean,
    )

    private val queueLock = Any()
    private val pendingWrites = linkedMapOf<Key, Write>()
    private val accepted = mutableMapOf<Key, Write>()
    private val leases = mutableMapOf<Key, WeakReference<Any>>()
    private var draining = false

    fun claim(preferences: SharedPreferences, key: String, lease: Any) {
        synchronized(queueLock) {
            leases.entries.removeAll { it.value.get() == null }
            leases[Key(preferences, key)] = WeakReference(lease)
        }
    }

    fun owns(preferences: SharedPreferences, key: String, lease: Any): Boolean =
        synchronized(queueLock) { leases[Key(preferences, key)]?.get() === lease }

    fun read(preferences: SharedPreferences, key: String): Any? {
        val queued = synchronized(queueLock) { accepted[Key(preferences, key)] }
        if (queued != null && queued.current()) return queued.value
        return storedValue(preferences, key)
    }

    fun unclaimedAndUnchanged(preferences: SharedPreferences, key: String, observed: Any?): Boolean =
        synchronized(queueLock) {
            val storageKey = Key(preferences, key)
            leases[storageKey]?.get() == null && accepted[storageKey]?.value == null &&
                storedValue(preferences, key) == observed
        }

    private fun storedValue(preferences: SharedPreferences, key: String): Any? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        preferences.all[key]
    }

    fun pending(preferences: SharedPreferences, key: String): Boolean {
        val queued = synchronized(queueLock) { accepted[Key(preferences, key)] }
        return queued != null && queued.current()
    }

    fun contains(preferences: SharedPreferences, key: String): Boolean {
        val queued = synchronized(queueLock) { accepted[Key(preferences, key)] }
        return if (queued != null && queued.current()) queued.value != null else preferences.contains(key)
    }

    fun enqueue(
        preferences: SharedPreferences,
        key: String,
        value: String?,
        failure: AtomicReference<Throwable?>,
        current: () -> Boolean,
    ) {
        synchronized(queueLock) {
            if (!current()) return
            val storageKey = Key(preferences, key)
            val write = Write(storageKey, value, failure, current)
            pendingWrites[storageKey] = write
            accepted[storageKey] = write
            if (!draining) {
                draining = true
                executor.execute(::drain)
            }
        }
    }

    fun flush() {
        executor.submit {}.get()
    }

    @SuppressLint("UseKtx")
    private fun drain() {
        while (true) {
            val write = synchronized(queueLock) {
                val first = pendingWrites.entries.firstOrNull()
                if (first == null) {
                    draining = false
                    return
                }
                pendingWrites.remove(first.key)
                first.value
            }
            try {
                synchronized(playbackPersistenceLock) {
                    if (write.current()) {
                        val editor = write.key.preferences.edit()
                        if (write.value == null) {
                            editor.remove(write.key.name)
                        } else {
                            editor.putString(write.key.name, write.value)
                        }
                        if (!editor.commit()) throw IOException("Cannot commit playback state")
                    }
                }
            } catch (error: Exception) {
                write.failure.compareAndSet(null, error)
                android.util.Log.e("StudioPlayback", "Cannot persist playback state", error)
            } finally {
                synchronized(queueLock) {
                    if (accepted[write.key] === write) accepted.remove(write.key)
                }
            }
        }
    }

    companion object {
        val shared = PlaybackPersistenceWriter()
    }
}
