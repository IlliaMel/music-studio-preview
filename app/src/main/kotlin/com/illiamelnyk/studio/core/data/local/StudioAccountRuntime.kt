package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.network.CatalogDto
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.playback.PlaybackMetadataSnapshot
import com.illiamelnyk.studio.core.domain.playback.PlaybackTrackMetadata
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.Track
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared owner transaction and durable account state. Feature repositories never own a second snapshot. */
internal class StudioAccountRuntime(
    context: Context,
    internal val store: StudioStore,
    internal val api: StudioApi,
    internal val dispatcher: CoroutineDispatcher,
    internal val generationWork: GenerationWorkScheduler,
    internal val notifications: GenerationNotifications,
    internal val clock: Clock = Clock.System,
) : StudioReferenceAccess,
    StudioCatalogAccess {
    internal val mutex = store.runtimeMutex
    internal val publicCatalogTracks = MutableStateFlow(DemoCatalog.tracks)
    internal val recordings = RecordingFiles(context)
    internal val recordingDrafts = RecordingDrafts(store, recordings, api)
    internal val notificationOutbox = NotificationOutbox(store)
    internal val deletions = AccountDeletions(store, api, recordings, notifications, recordingDrafts)

    @Volatile internal var deletingAccountId: String? = null
    internal val state = MutableStateFlow<AccountSnapshot?>(null)
    private val initialization = Mutex()
    private var initialized = false
    private var revision = -1L
    private var storageUncertain = false
    private val observing = AtomicBoolean(false)

    internal suspend fun initialize() = initialization.withLock {
        if (initialized) return@withLock
        store.initialize()
        mutex.withLock { refreshStorageStateLocked() }
        initialized = true
    }

    /** Caller holds the shared runtime mutex; always consult durable ownership before side effects. */
    internal suspend fun refreshStorageStateLocked() {
        deletions.load()
        notificationOutbox.load()
        publish(store.readActive(), authoritative = true)
    }

    /** The caller holds the owner mutex until the durable revision has been acknowledged. */
    internal suspend fun commitStateChange(action: suspend () -> ActiveAccountChange) {
        durableMutation { publish(action()) }
    }

    internal suspend fun executeDeletion(intent: AccountDeletionIntent): AccountDeletionResult =
        durableMutation { deletions.execute(intent) }

    private suspend fun <T> durableMutation(action: suspend () -> T): T {
        var original: Throwable? = null
        try {
            return action()
        } catch (error: Throwable) {
            original = error
            throw error
        } finally {
            try {
                withContext(NonCancellable) { refreshStorageStateLocked() }
            } catch (handoff: Throwable) {
                closeUncertainAccess()
                val primary = original
                if (primary == null) throw handoff
                if (primary !== handoff) primary.addSuppressed(handoff)
            }
        }
    }

    internal suspend fun signOutActive(
        token: String?,
        clearActive: suspend () -> ActiveAccountChange = { store.clearActive() },
    ) {
        var original: Throwable? = null
        try {
            commitStateChange(clearActive)
        } catch (error: Throwable) {
            original = error
            throw error
        } finally {
            withContext(NonCancellable) {
                try {
                    // Query durable ownership, including when cancellation hid a successful clear return.
                    if (store.isSignedOut()) {
                        sync.cancelForSignOut()
                        api.clearClientSession(token)
                        reconcileGenerationWork()
                        recordNotifications()
                    }
                } catch (cleanup: Throwable) {
                    val primary = original
                    if (primary == null) throw cleanup
                    if (primary !== cleanup) primary.addSuppressed(cleanup)
                }
            }
        }
    }

    /** Uncertain local ownership never permits an old queued Flow snapshot to reopen access. */
    private fun closeUncertainAccess() {
        storageUncertain = true
        referenceAccess = null
        state.value = null
    }

    internal fun observeStorage(scope: CoroutineScope) {
        if (!observing.compareAndSet(false, true)) return
        scope.launch(dispatcher) {
            var failures = 0
            while (true) {
                try {
                    store.observeActiveAccount().collect { change ->
                        mutex.withLock { publish(change) }
                        failures = 0
                    }
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A row that cannot be read closes access instead of crashing the app; the next
                    // authoritative refresh reopens it, and observing starts again after a pause.
                    android.util.Log.w("StudioData", "Cannot observe stored account: " + error.javaClass.simpleName)
                    mutex.withLock { closeUncertainAccess() }
                    delay(1_000L shl failures.coerceAtMost(5))
                    failures++
                }
            }
        }
    }
    private data class ReferenceAccess(val ownerId: String, val directory: java.io.File)

    @Volatile private var referenceAccess: ReferenceAccess? = null

    private fun prepareReferenceAccess(account: AccountSnapshot): ReferenceAccess {
        val owner = account.session.user.id.value
        val reusable = referenceAccessOrNull(owner)?.takeIf {
            state.value?.session?.user?.id?.value == owner && deletingAccountId != owner
        }
        return reusable ?: ReferenceAccess(owner, recordings.referenceDirectory(owner))
    }

    private fun referenceAccessOrNull(owner: String): ReferenceAccess? = referenceAccess?.takeIf { it.ownerId == owner }
    val playbackMetadata = state.map { it?.toPlaybackMetadata() }.distinctUntilChanged()

    fun isPlaybackMetadataCurrent(snapshot: PlaybackMetadataSnapshot): Boolean {
        val current = state.value ?: return false
        return deletingAccountId != snapshot.ownerId && current.toPlaybackMetadata() == snapshot
    }

    internal fun AccountSnapshot.toPlaybackMetadata() = PlaybackMetadataSnapshot(
        session.user.id.value,
        tracks.map {
            PlaybackTrackMetadata(it.id, it.title, it.artist, it.coverSeed)
        },
    )

    val activeAccountId: String?
        get() = state.value?.session?.user?.id?.value?.takeUnless { it == deletingAccountId }

    /** Main-safe callback: the directory is prepared only when account state is loaded/saved on IO. */
    override fun referenceDirectory(): java.io.File {
        val owner = state.value?.session?.user?.id?.value ?: throw ApiFailure(AppError.Unauthorized)
        if (owner == deletingAccountId) throw ApiFailure(AppError.Unauthorized)
        return referenceAccessOrNull(owner)?.directory ?: throw ApiFailure(AppError.Unauthorized)
    }

    override fun registerCatalogTracks(tracks: List<Track>) {
        publicCatalogTracks.value =
            tracks.distinctBy { it.id }.map { it.copy(isLiked = false) }
    }

    override fun catalogTrack(track: Track): Track {
        val account = state.value ?: return track.copy(isLiked = false)
        val liked = track.id.value in account.likes
        if (account.token == null) return track.copy(isLiked = liked)
        return track.copy(
            isLiked = liked,
            audioUri = api.catalogAudioUri(track.id),
        )
    }

    internal val sync = StudioSyncCoordinator(this)
    internal suspend fun refresh() = sync.refresh()
    internal fun reconcileGenerationWork() = sync.reconcileGenerationWork()
    internal suspend fun recordNotifications() = sync.recordNotifications()
    internal suspend fun syncPending(ownerId: String) = sync.syncPending(ownerId)

    internal val creditData = DurableCreditsRepository(this)
    internal suspend fun updateQuota() = creditData.refresh()
    internal fun requireAccount(): AccountSnapshot {
        val account = state.value ?: throw ApiFailure(AppError.Unauthorized)
        if (deletions.pending().any {
                it.ownerId == account.session.user.id.value
            }
        ) {
            throw ApiFailure(AppError.Unauthorized)
        }
        return account
    }
    internal fun resetCredits(account: AccountSnapshot) = creditData.reset(account)
    internal suspend fun save(
        account: AccountSnapshot,
        generationRequests: Map<String, GenerationRequest> = emptyMap(),
        completeAttempt: Boolean = false,
        retryLinks: Map<String, String> = emptyMap(),
        removedTracks: Set<String> = emptySet(),
    ) {
        commitStateChange {
            store.saveAccount(account, generationRequests, completeAttempt, retryLinks, removedTracks)
        }
        reconcileGenerationWork()
        recordNotifications()
    }

    internal suspend fun publish(change: ActiveAccountChange, authoritative: Boolean = false) {
        if (storageUncertain && !authoritative) return
        if (change.revision < revision) return
        val account = change.account?.takeUnless {
            (!api.isAvailable && it.token != null) ||
                deletions.pending().any { intent -> intent.ownerId == it.session.user.id.value }
        }
        referenceAccess = account?.let { withContext(Dispatchers.IO) { prepareReferenceAccess(it) } }
        change.outbox?.let(notificationOutbox::accept)
        revision = change.revision
        storageUncertain = false
        state.value = account
    }
    internal val audio = StudioAudioAccess(this)
    suspend fun exportHeaders(track: Track) = audio.exportHeaders(track)
    suspend fun playbackHeaders(track: Track) = audio.playbackHeaders(track)

    override suspend fun fetchCatalog(): Outcome<CatalogDto> = operation {
        val account = requireAccount()
        api.getCatalog(account.token ?: throw ApiFailure(AppError.UnsupportedMode))
    }

    internal suspend fun loadActive(): AccountSnapshot? = store.readActive().account?.takeUnless {
        (!api.isAvailable && it.token != null) ||
            deletions.pending().any { intent -> intent.ownerId == it.session.user.id.value }
    }
    internal suspend fun loadAccount(id: String): AccountSnapshot? = store.readAccount(id)

    internal suspend fun <T> operation(action: suspend () -> T): Outcome<T> = withContext(dispatcher) {
        initialize()
        mutex.withLock {
            try {
                refreshStorageStateLocked()
                Outcome.Success(action())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ApiFailure) {
                Outcome.Failure(failure.error)
            } catch (error: Exception) {
                android.util.Log.e("StudioData", "Data operation failed: " + error.javaClass.simpleName)
                Outcome.Failure(AppError.Unknown)
            } finally {
                guarded { notifications.deliver(activeAccountId) }
                reconcileGenerationWork()
            }
        }
    }
    internal suspend fun guarded(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiFailure) {
            if (error.error == AppError.Unauthorized) {
                android.util.Log.w(
                    "StudioData",
                    "Server session unavailable; cached data retained",
                )
            }
        } catch (error: Exception) {
            android.util.Log.e("StudioData", "Refresh failed: " + error.javaClass.simpleName)
        }
    }
}
