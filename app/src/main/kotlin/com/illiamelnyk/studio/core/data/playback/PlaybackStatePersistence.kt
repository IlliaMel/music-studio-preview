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
    private val lease = Any()

    fun claim(owner: String) = writer.claim(preferences, playbackPreferenceKey(owner), lease)

    fun owns(owner: String): Boolean = writer.owns(preferences, playbackPreferenceKey(owner), lease)

    fun read(key: String): String? = writer.read(preferences, key)

    fun contains(key: String): Boolean = writer.contains(preferences, key)

    fun pending(key: String): Boolean = writer.pending(preferences, key)

    fun write(key: String, value: String, owner: String, generation: Long, current: () -> Boolean) {
        writer.enqueue(preferences, key, value, failure) {
            current() && owns(owner) && ownerId() == owner && playbackStateGeneration(owner) == generation
        }
    }

    fun removeQueue(owner: String, generation: Long) {
        writer.enqueue(preferences, playbackPreferenceKey(owner), null, failure) {
            owns(owner) && playbackStateGeneration(owner) == generation
        }
    }

    fun remove(keys: Collection<String>) {
        keys.forEach { writer.enqueue(preferences, it, null, failure) { true } }
    }

    /** Explicit durability boundary. Call only after releasing the playback lock. */
    fun flush() {
        writer.flush()
        failure.getAndSet(null)?.let { throw IOException("Cannot persist playback state", it) }
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

    fun read(preferences: SharedPreferences, key: String): String? {
        val queued = synchronized(queueLock) { accepted[Key(preferences, key)] }
        if (queued != null && queued.current()) return queued.value
        return preferences.getString(key, null)
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
