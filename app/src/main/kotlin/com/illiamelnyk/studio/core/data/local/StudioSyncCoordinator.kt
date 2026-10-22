package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.network.toSession
import com.illiamelnyk.studio.core.data.network.toTrack
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricWord
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns startup recovery and foreground polling; durable background scheduling remains owner-scoped. */
internal class StudioSyncCoordinator(private val runtime: StudioAccountRuntime) : StudioSynchronization {
    private val started = AtomicBoolean(false)
    private val foreground = MutableStateFlow(true)

    @Volatile private var initialRefreshOwner: String? = null

    /** Accounts whose library this process has asked the server for, successfully or not. */
    internal val refreshedOwners = MutableStateFlow<Set<String>>(emptySet())

    internal fun requestInitialRefresh(ownerId: String) {
        initialRefreshOwner = ownerId
    }
    override val activeAccountId: String? get() = runtime.activeAccountId
    override fun setForeground(active: Boolean) {
        foreground.value = active
    }

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch(runtime.dispatcher) {
            var initialized = false
            runtime.guarded {
                runtime.initialize()
                runtime.observeStorage(scope)
                initialized = true
                runtime.mutex.withLock {
                    runtime.refreshStorageStateLocked()
                    resumeDeletions()
                    reconcileGenerationWork()
                    recordNotifications()
                    runtime.notifications.deliver(runtime.activeAccountId)
                    reconcileGenerationWork()
                }
            }
            if (!initialized) {
                started.set(false)
                return@launch
            }
            combine(
                foreground,
                runtime.state.map { account ->
                    account?.takeUnless { it.requiresAuthentication }?.session?.user?.id?.value to
                        account?.tracks.orEmpty().any {
                            it.status == TrackStatus.Queued || it.status is TrackStatus.Generating
                        }
                }.distinctUntilChanged(),
            ) { visible, account -> Triple(visible, account.first, account.second) }
                .distinctUntilChanged()
                .collectLatest { (visible, owner, pending) ->
                    if (!visible || (owner == null && !hasDeletionRecovery())) return@collectLatest
                    var firstRound = true
                    while (true) {
                        if (owner == null && !hasDeletionRecovery()) return@collectLatest
                        // Wait for sign-in's owner transaction before deciding: its normal refresh
                        // may have finished already. Only an interrupted initial refresh skips delay.
                        val immediate = firstRound && owner != null &&
                            runtime.mutex.withLock { initialRefreshOwner == owner }
                        firstRound = false
                        if (!immediate) delay(if (pending) 2_000 else 30_000)
                        // A round that sees a generation finish flips `pending`, which restarts this
                        // loop: let that round finish its notifications, quota and acknowledgements.
                        withContext(NonCancellable) {
                            runtime.guarded {
                                runtime.mutex.withLock {
                                    runtime.refreshStorageStateLocked()
                                    if (System.currentTimeMillis() - lastDeletionRetryMillis >= 30_000) {
                                        resumeDeletions()
                                    }
                                    if (runtime.activeAccountId != owner) return@withLock
                                    refresh()
                                    if (runtime.activeAccountId?.let(runtime.notificationOutbox::isDirty) ==
                                        true
                                    ) {
                                        recordNotifications()
                                    }
                                    runtime.notifications.deliver(runtime.activeAccountId)
                                    reconcileGenerationWork()
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun hasDeletionRecovery(): Boolean = runtime.deletions.pending().any {
        !(it.localPurged && (it.remoteCompletionUnverified || it.requiresReauthentication))
    }

    override suspend fun recoverPendingDeletions(): Outcome<Boolean> = runtime.operation {
        resumeDeletions()
        hasDeletionRecovery()
    }

    internal suspend fun resumeDeletions() {
        lastDeletionRetryMillis = System.currentTimeMillis()
        runtime.deletions.pending().filterNot {
            it.localPurged && (it.remoteCompletionUnverified || it.requiresReauthentication)
        }.forEach { intent ->
            runtime.guarded {
                val result = runtime.executeDeletion(intent)
                if (runtime.deletingAccountId == result.ownerId) runtime.deletingAccountId = null
                if (result.remoteError == null || result.remoteCompletionUnverified ||
                    result.requiresReauthentication
                ) {
                    runtime.api.clearClientSession(intent.token)
                }
                reconcileGenerationWork()
                recordNotifications()
            }
        }
    }

    override suspend fun syncPending(ownerId: String): Outcome<Boolean> = runtime.operation {
        val account = runtime.state.value
        if (runtime.activeAccountId != ownerId) {
            reconcileGenerationWork()
            return@operation false
        }
        recordNotifications()
        if (needsGenerationSync(account)) refresh()
        runtime.guarded { runtime.notifications.deliver(ownerId) }
        reconcileGenerationWork()
        needsGenerationSync(runtime.state.value) || needsNotificationSync(runtime.state.value)
    }

    internal fun needsNotificationSync(account: AccountSnapshot?): Boolean {
        val owner = runtime.activeAccountId ?: return false
        if (account?.token == null || account.requiresAuthentication ||
            account.session.user.id.value != owner
        ) {
            return false
        }
        return try {
            runtime.notificationOutbox.isDirty(owner) || runtime.notifications.hasPending(owner)
        } catch (_: Exception) {
            true
        }
    }

    internal fun needsGenerationSync(account: AccountSnapshot?): Boolean = account != null &&
        account.token != null && !account.requiresAuthentication &&
        account.session.user.id.value != runtime.deletingAccountId &&
        runtime.deletions.pending().none { it.ownerId == account.session.user.id.value } &&
        account.tracks.any { it.status == TrackStatus.Queued || it.status is TrackStatus.Generating }

    internal fun cancelForSignOut() {
        initialRefreshOwner = null
        try {
            runtime.generationWork.reconcile(null)
        } catch (error: Exception) {
            android.util.Log.w("StudioData", "Cannot cancel signed-out work: " + error.javaClass.simpleName)
        }
    }

    internal fun reconcileGenerationWork() {
        val account = runtime.state.value
        val pending = needsGenerationSync(account) || needsNotificationSync(account)
        val owner = account?.session?.user?.id?.value?.takeIf { pending }
            ?: "account-deletion".takeIf { hasDeletionRecovery() }
        try {
            val active = runtime.activeAccountId
            if (owner ==
                null
            ) {
                runtime.generationWork.complete(active ?: "account-deletion")
            } else {
                runtime.generationWork.reconcile(owner)
            }
        } catch (error: Exception) {
            android.util.Log.w("StudioData", "Cannot schedule generation sync: " + error.javaClass.simpleName)
        }
    }

    internal suspend fun refresh() {
        val account = runtime.state.value ?: return
        try {
            if (account.requiresAuthentication) return
            refreshAccount(account)
            if (initialRefreshOwner == account.session.user.id.value) initialRefreshOwner = null
        } catch (failure: ApiFailure) {
            if (failure.error == AppError.Unauthorized) {
                val current = runtime.state.value
                if (current?.session?.user?.id == account.session.user.id && current.token == account.token) {
                    runtime.save(current.copy(requiresAuthentication = true))
                }
            }
            throw failure
        } finally {
            // Whatever came of it, nothing more is on its way for now: an empty library is empty.
            refreshedOwners.update { it + account.session.user.id.value }
        }
    }

    private suspend fun refreshAccount(cached: AccountSnapshot) {
        var account = cached
        if (runtime.deletions.pending().any { it.ownerId == account.session.user.id.value }) return
        if (account.token == null) {
            val tracks = account.tracks.map { track ->
                if (track.status == TrackStatus.Queued || track.status is TrackStatus.Generating) {
                    val elapsed = (runtime.clock.now() - track.createdAt).inWholeMilliseconds
                    val status = when {
                        elapsed >= 4_000 && track.prompt.contains(
                            "in the style of",
                            true,
                        ) ->
                            TrackStatus.Failed(AppError.InvalidPrompt("Original music with a similar mood"))

                        elapsed >= 4_000 -> TrackStatus.Ready

                        else -> TrackStatus.Generating((elapsed / 4_000f).coerceIn(0f, 0.99f))
                    }
                    track.copy(status = status)
                } else {
                    track
                }
            }
            val next = runtime.resetCredits(account).copy(tracks = tracks)
            if (next != account) runtime.save(next)
        } else {
            val token = account.token
            val profile = runtime.api.getProfile(token).toSession(account.session.dataMode)
            if (profile.user.id != account.session.user.id) throw ApiFailure(AppError.Unauthorized)
            account = account.copy(session = profile)
            // Persist a confirmed grant/revocation even when a later library endpoint is unavailable.
            if (account != cached) runtime.save(account)
            runtime.updateQuota()
            account = runtime.requireAccount()
            val serverLikes = runtime.api.listLikes(token).toSet()
            val likes = serverLikes + account.likes.filter { it.startsWith("recording-") }
            val remote = mutableListOf<Track>()
            var cursor: String? = null
            do {
                val page = runtime.api.listTracks(token, cursor)
                for (dto in page.tracks) {
                    var fresh = dto.toTrack(account.session.user.displayName.orEmpty())
                    if (fresh.status == TrackStatus.Ready) {
                        val cached = runtime.store.detailFor(account.session.user.id.value, fresh.id.value)
                        val detailed = if (cached == null) {
                            runtime.api.getTrack(
                                token,
                                fresh.id,
                            ).toTrack(account.session.user.displayName.orEmpty()).also {
                                runtime.store.saveDetail(account.session.user.id.value, it)
                            }
                        } else {
                            fresh.copy(
                                customLyrics = cached.customLyrics,
                                words = cached.words.map { LyricWord(it.text, it.start, it.end) },
                            )
                        }
                        fresh = fresh.copy(words = detailed.words, customLyrics = detailed.customLyrics)
                    }
                    remote += fresh.copy(
                        isLiked = fresh.id.value in likes,
                    )
                }
                cursor = page.nextCursor
            } while (cursor != null)
            // Publish recovered songs before optional recordings/playlists can delay or fail.
            val localRecordings = account.tracks.filter { it.id.isRecording }
            account =
                account.copy(tracks = (remote + localRecordings).sortedByDescending { it.createdAt }, likes = likes)
            runtime.save(account)
            val recoveredDrafts = runtime.recordingDrafts.recover(account)
            val serverDrafts = runtime.api.listRecordings(token)
                .map { runtime.recordingDrafts.track(it, account) }
            val remoteDraftIds = serverDrafts.map { it.id }.toSet()
            val localAndRemoteRecordings = serverDrafts + account.tracks.filter {
                it.id.value.startsWith("recording-") && it.id !in remoteDraftIds &&
                    it.audioUri?.startsWith("file:") == true
            }
            val order = compareByDescending<Track> { it.createdAt }
                .thenBy { it.generationId }.thenBy { it.variantIndex }
            val allTracks = (remote + localAndRemoteRecordings).sortedWith(order)
            val serverPlaylists = runtime.api.listPlaylists(token)
            val playlists = serverPlaylists.map { dto ->
                val id = dto.id
                val members = dto.trackIds.mapNotNull { trackId ->
                    allTracks.find { it.id.value == trackId }
                        ?: runtime.publicCatalogTracks.value.find { it.id.value == trackId }?.let(runtime::catalogTrack)
                }
                val localMembers = account.playlists.find { it.id == id }?.tracks.orEmpty()
                    .filter { it.id.value.startsWith("recording-") }
                    .mapNotNull { member -> allTracks.find { it.id == member.id } }
                Playlist(id, dto.name, (members + localMembers).distinctBy { it.id })
            }
            runtime.save(account.copy(tracks = allTracks, likes = likes, playlists = playlists))
            runtime.guarded { runtime.recordingDrafts.acknowledge(recoveredDrafts) }
            runtime.updateQuota()
        }
    }

    internal suspend fun recordNotifications() {
        try {
            val owner = runtime.activeAccountId
            if (owner != null) runtime.notificationOutbox.markDirty(owner)
            val observed = owner?.let(runtime.notificationOutbox::observed).orEmpty()
            runtime.notifications.recover(owner, runtime.state.value?.tracks.orEmpty(), observed)
            if (owner != null) runtime.notificationOutbox.recorded(owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("StudioData", "Cannot record completion: " + error.javaClass.simpleName)
        }
    }

    private var lastDeletionRetryMillis = 0L
}
