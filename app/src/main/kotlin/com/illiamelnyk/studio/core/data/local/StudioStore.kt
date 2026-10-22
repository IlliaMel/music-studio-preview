package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.illiamelnyk.studio.core.data.local.database.ActiveOwnerEntity
import com.illiamelnyk.studio.core.data.local.database.DeletionIntentEntity
import com.illiamelnyk.studio.core.data.local.database.GENERATION_PERMISSION_MIGRATION
import com.illiamelnyk.studio.core.data.local.database.GenerationAttemptEntity
import com.illiamelnyk.studio.core.data.local.database.GenerationRequestEntity
import com.illiamelnyk.studio.core.data.local.database.NotificationOutboxEntity
import com.illiamelnyk.studio.core.data.local.database.RecordingDraftEntity
import com.illiamelnyk.studio.core.data.local.database.RecordingReceiptEntity
import com.illiamelnyk.studio.core.data.local.database.ReferenceUploadEntity
import com.illiamelnyk.studio.core.data.local.database.RetryLinkEntity
import com.illiamelnyk.studio.core.data.local.database.StoreMetadataEntity
import com.illiamelnyk.studio.core.data.local.database.StoredActiveAccount
import com.illiamelnyk.studio.core.data.local.database.StoredLikeEntity
import com.illiamelnyk.studio.core.data.local.database.StoredMembershipEntity
import com.illiamelnyk.studio.core.data.local.database.StoredOwnedTrackEntity
import com.illiamelnyk.studio.core.data.local.database.StoredPlaylistEntity
import com.illiamelnyk.studio.core.data.local.database.StoredWord
import com.illiamelnyk.studio.core.data.local.database.StudioDatabase
import com.illiamelnyk.studio.core.data.local.database.StudioLegacyImporter
import com.illiamelnyk.studio.core.data.local.database.StudioTypedDao
import com.illiamelnyk.studio.core.data.local.database.TrackDetailEntity
import com.illiamelnyk.studio.core.data.local.database.accountEntity
import com.illiamelnyk.studio.core.data.local.database.entity
import com.illiamelnyk.studio.core.data.local.database.intent
import com.illiamelnyk.studio.core.data.local.database.snapshot
import com.illiamelnyk.studio.core.data.local.database.storedFields
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class LegacyStudioStateCorruptedException(cause: Throwable) :
    IOException("Cannot migrate legacy studio state; source retained", cause)

internal data class ActiveAccountChange(
    val revision: Long,
    val account: AccountSnapshot?,
    val outbox: NotificationOutboxEntity? = null,
)

internal data class PendingGeneration(
    val key: String,
    val request: GenerationRequest,
    val variants: Int,
    val blocked: Boolean = false,
)

/** Typed suspend persistence. JSON documents are read only by the one-time legacy importer. */
class StudioStore(context: Context) {
    private val appContext = context.applicationContext
    private val handle = handle(appContext)
    internal val database: StudioDatabase get() = handle.database
    internal val runtimeMutex: Mutex get() = handle.runtimeMutex

    suspend fun initialize() = handle.initialization.withLock {
        if (handle.initialized) return@withLock
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val dao = database.typedDao()
                // Earlier versions kept the whole library in one JSON file: import it once, directly.
                if (dao.metadata(LEGACY_IMPORT) == null) {
                    val file = File(appContext.filesDir, LEGACY_FILE)
                    val root = if (file.exists()) {
                        try {
                            JSONObject(file.readText())
                        } catch (error: org.json.JSONException) {
                            throw LegacyStudioStateCorruptedException(error)
                        }
                    } else {
                        JSONObject()
                    }
                    StudioLegacyImporter.import(database, root)
                    dao.putMetadata(StoreMetadataEntity(LEGACY_IMPORT, "1"))
                }
                if (dao.activeAccount() == null) dao.putActiveOwner(ActiveOwnerEntity(0, null, 0))
            }
            // The import committed, so the file is no longer a second copy of the library.
            File(appContext.filesDir, LEGACY_FILE).delete()
            File(appContext.filesDir, LEGACY_FILE + ".new").delete()
        }
        handle.initialized = true
    }

    internal suspend fun readActive(): ActiveAccountChange {
        initialize()
        return database.typedDao().activeAccount().change()
    }

    internal fun observeActiveAccount(): Flow<ActiveAccountChange> =
        database.typedDao().observeActiveAccount().map { it.change() }

    internal suspend fun readAccount(owner: String): AccountSnapshot? {
        initialize()
        return database.typedDao().account(owner)?.snapshot()
    }

    /** Upserts changed rows; parent accounts are never REPLACEd or deleted for an edit. */
    internal suspend fun saveAccount(
        account: AccountSnapshot,
        generationRequests: Map<String, GenerationRequest> = emptyMap(),
        completeAttempt: Boolean = false,
        retryLinks: Map<String, String> = emptyMap(),
        removedTracks: Set<String> = emptySet(),
    ): ActiveAccountChange {
        initialize()
        return database.withTransaction {
            val dao = database.typedDao()
            persistAccount(dao, account)
            val owner = account.session.user.id.value
            generationRequests.forEach { (id, request) ->
                dao.putGenerationRequest(GenerationRequestEntity(id, owner, request.storedFields()))
            }
            retryLinks.forEach { (original, replacement) ->
                dao.putRetryLink(RetryLinkEntity(original, owner, replacement))
            }
            if (completeAttempt) dao.deleteAttempt(owner)
            removedTracks.forEach { clearTrackJournal(dao, owner, it) }
            val outbox = registerNotifications(dao, account)
            val revision = (dao.activeAccount()?.active?.revision ?: 0L) + 1
            dao.putActiveOwner(ActiveOwnerEntity(0, owner, revision))
            ActiveAccountChange(revision, account, outbox)
        }
    }

    internal suspend fun isSignedOut(): Boolean {
        initialize()
        return database.typedDao().activeAccount()?.active?.ownerId == null
    }

    internal suspend fun clearActive(expectedOwner: String? = null): ActiveAccountChange {
        initialize()
        return database.withTransaction {
            val dao = database.typedDao()
            val active = dao.activeAccount()
            if (expectedOwner != null &&
                active?.active?.ownerId != expectedOwner
            ) {
                return@withTransaction active.change()
            }
            val next = ActiveOwnerEntity(0, null, (active?.active?.revision ?: 0) + 1)
            dao.putActiveOwner(next)
            ActiveAccountChange(next.revision, null)
        }
    }

    internal suspend fun generationAttempt(owner: String): PendingGeneration? {
        initialize()
        return database.typedDao().attempt(owner)?.let {
            PendingGeneration(it.attemptKey, it.fields.request(), it.variants, it.blocked)
        }
    }

    internal suspend fun putGenerationAttempt(owner: String, value: PendingGeneration) {
        initialize()
        database.typedDao().putGenerationAttempt(
            GenerationAttemptEntity(owner, value.key, value.variants, value.request.storedFields(), value.blocked),
        )
    }

    internal suspend fun clearGenerationAttempt(owner: String, expectedKey: String) {
        initialize()
        database.typedDao().deleteAttemptIfCurrent(owner, expectedKey)
    }

    internal suspend fun requestFor(owner: String, id: String): GenerationRequest? {
        if (owner.isEmpty()) return null
        initialize()
        return database.typedDao().generationRequest(owner, id)?.fields?.request()
    }

    internal suspend fun retryFor(owner: String, id: String): String? {
        if (owner.isEmpty()) return null
        initialize()
        return database.typedDao().retry(owner, id)?.replacementId
    }

    internal suspend fun uploadFor(owner: String, sourceHash: String): String? {
        initialize()
        return database.typedDao().upload(owner, ownerHash(sourceHash))?.uploadId
    }

    internal suspend fun saveUpload(owner: String, sourceHash: String, upload: String) {
        initialize()
        database.typedDao().putReferenceUpload(ReferenceUploadEntity(owner, ownerHash(sourceHash), upload))
    }

    internal suspend fun detailFor(owner: String, id: String): TrackDetailEntity? {
        if (owner.isEmpty()) return null
        initialize()
        return database.typedDao().detail(owner, id)
    }

    internal suspend fun saveDetail(owner: String, track: Track) {
        initialize()
        database.typedDao().putTrackDetail(
            TrackDetailEntity(
                track.id.value,
                owner,
                track.customLyrics,
                track.words.map { StoredWord(it.text, it.startMs, it.endMs) },
            ),
        )
    }

    internal suspend fun pendingDeletions(): List<AccountDeletionIntent> {
        initialize()
        return database.typedDao().allDeletionIntent().map { it.intent() }
    }

    internal suspend fun saveDeletion(value: AccountDeletionIntent) {
        initialize()
        database.typedDao().putDeletionIntent(value.entity())
    }

    internal suspend fun deleteDeletion(owner: String) {
        initialize()
        database.typedDao().deleteDeletion(owner)
    }

    /** Cache purge and its durable deletion phase commit together; other owners remain untouched. */
    internal suspend fun purgeAccount(intent: AccountDeletionIntent): ActiveAccountChange {
        initialize()
        return database.withTransaction {
            val dao = database.typedDao()
            val owner = intent.ownerId
            dao.deleteAccount(owner)
            dao.deleteAttempt(owner)
            dao.deleteUploads(owner)
            dao.deleteRecordingDrafts(ownerHash(owner))
            dao.deleteRecordingReceipts(ownerHash(owner))
            dao.deleteOutbox(owner)
            dao.deleteOwnerRequests(owner)
            dao.deleteOwnerRetries(owner)
            dao.deleteOwnerDetails(owner)
            intent.audio.forEach {
                clearTrackJournal(dao, owner, it.id)
                clearTrackJournal(dao, "", it.id)
            }
            if (intent.remoteConfirmed) {
                dao.deleteDeletion(owner)
            } else {
                dao.putDeletionIntent(
                    intent.copy(audio = emptyList(), references = emptyList(), localPurged = true).entity(),
                )
            }
            val active = dao.activeAccount()
            if (active?.active?.ownerId == owner) {
                val next = ActiveOwnerEntity(0, null, active.active.revision + 1)
                dao.putActiveOwner(next)
                ActiveAccountChange(next.revision, null)
            } else {
                active.change()
            }
        }
    }

    internal suspend fun recordingDraft(key: String): RecordingDraftEntity? {
        initialize()
        return database.typedDao().recordingDraft(key)
    }

    internal suspend fun recordingReceipt(key: String): String? {
        initialize()
        return database.typedDao().recordingReceipt(key)?.trackId
    }

    internal suspend fun recordingDrafts(owner: String): List<RecordingDraftEntity> {
        initialize()
        return database.typedDao().recordingDrafts(ownerHash(owner))
    }

    internal suspend fun saveRecordingDraft(value: RecordingDraftEntity) {
        initialize()
        database.typedDao().putRecordingDraft(value)
    }

    internal suspend fun confirmRecording(value: RecordingDraftEntity, trackId: String) {
        initialize()
        database.withTransaction {
            database.typedDao().putRecordingReceipt(
                RecordingReceiptEntity(value.key, value.ownerId, value.ownerHash, trackId),
            )
            database.typedDao().putRecordingDraft(value.copy(confirmed = true))
        }
    }

    internal suspend fun deleteRecordingDraft(key: String) {
        initialize()
        database.typedDao().deleteRecordingDraft(key)
    }

    internal suspend fun outboxes(): List<NotificationOutboxEntity> {
        initialize()
        return database.typedDao().allNotificationOutbox()
    }

    internal suspend fun setOutboxDirty(owner: String, dirty: Boolean): NotificationOutboxEntity {
        initialize()
        return database.withTransaction {
            val value = (
                database.typedDao().outbox(
                    owner,
                ) ?: NotificationOutboxEntity(owner, emptyList(), false)
                ).copy(dirty = dirty)
            database.typedDao().putNotificationOutbox(value)
            value
        }
    }

    private fun StoredActiveAccount?.change(): ActiveAccountChange =
        ActiveAccountChange(this?.active?.revision ?: 0, this?.accounts?.singleOrNull()?.snapshot())

    companion object {
        internal const val DATABASE_NAME = "studio.db"
        internal const val LEGACY_FILE = "studio-state-v1.json"
        internal const val LEGACY_IMPORT = "legacy_json_import"
        private class Handle(
            val database: StudioDatabase,
            val initialization: Mutex = Mutex(),
            val runtimeMutex: Mutex = Mutex(),
            var initialized: Boolean = false,
        )
        private val handles = mutableMapOf<String, Handle>()
        private fun handle(context: Context): Handle = synchronized(handles) {
            handles.getOrPut(context.getDatabasePath(DATABASE_NAME).absolutePath) {
                Handle(
                    Room.databaseBuilder(context.applicationContext, StudioDatabase::class.java, DATABASE_NAME)
                        .addMigrations(GENERATION_PERMISSION_MIGRATION).build(),
                )
            }
        }

        internal fun ownerHash(owner: String): String = MessageDigest.getInstance("SHA-256")
            .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        internal suspend fun persistAccount(dao: StudioTypedDao, account: AccountSnapshot) {
            val owner = account.session.user.id.value
            val previous = dao.account(owner)
            val header = account.accountEntity()
            if (previous?.account != header) dao.putStoredAccount(header)
            val tracks = account.tracks.mapIndexed { position, track ->
                StoredOwnedTrackEntity(owner, track.id.value, position, track.storedFields())
            }
            val oldTracks = previous?.tracks.orEmpty().associateBy { it.trackId }
            val newTracks = tracks.associateBy { it.trackId }
            (oldTracks.keys - newTracks.keys).forEach { dao.deleteTrack(owner, it) }
            tracks.filter { oldTracks[it.trackId] != it }.forEach { dao.putStoredOwnedTrack(it) }
            val playlists = account.playlists.mapIndexed { position, value ->
                StoredPlaylistEntity(owner, value.id, value.name, position)
            }
            val oldPlaylists = previous?.playlists.orEmpty().associateBy { it.playlistId }
            val newPlaylists = playlists.associateBy { it.playlistId }
            (oldPlaylists.keys - newPlaylists.keys).forEach { dao.deletePlaylist(owner, it) }
            playlists.filter { oldPlaylists[it.playlistId] != it }.forEach { dao.putStoredPlaylist(it) }
            val members = account.playlists.flatMap { list ->
                list.tracks.mapIndexed { position, track ->
                    StoredMembershipEntity(owner, list.id, position, track.id.value, track.storedFields())
                }
            }
            val oldMembers = previous?.members.orEmpty().associateBy { it.playlistId to it.position }
            val newMembers = members.associateBy { it.playlistId to it.position }
            (oldMembers.keys - newMembers.keys).forEach { (list, position) -> dao.deleteMember(owner, list, position) }
            members.filter { oldMembers[it.playlistId to it.position] != it }.forEach { dao.putStoredMembership(it) }
            val likes = account.likes.mapIndexed { position, id -> StoredLikeEntity(owner, id, position) }
            val oldLikes = previous?.likes.orEmpty().associateBy { it.trackId }
            val newLikes = likes.associateBy { it.trackId }
            (oldLikes.keys - newLikes.keys).forEach { dao.deleteLike(owner, it) }
            likes.filter { oldLikes[it.trackId] != it }.forEach { dao.putStoredLike(it) }
        }

        internal suspend fun registerNotifications(
            dao: StudioTypedDao,
            account: AccountSnapshot,
        ): NotificationOutboxEntity {
            val owner = account.session.user.id.value
            val ids = account.tracks.map { it.id.value }.toSet()
            val observed = dao.outbox(owner)?.observedIds.orEmpty().filter { it in ids } +
                account.tracks.filter {
                    it.status == TrackStatus.Queued || it.status is TrackStatus.Generating
                }.map { it.id.value }
            return NotificationOutboxEntity(owner, observed.distinct(), true).also { dao.putNotificationOutbox(it) }
        }

        private suspend fun clearTrackJournal(dao: StudioTypedDao, owner: String, id: String) {
            dao.deleteRequest(owner, id)
            dao.deleteRetry(owner, id)
            dao.deleteDetail(owner, id)
        }

        internal fun closeForTests(context: Context) {
            synchronized(handles) {
                handles.remove(context.getDatabasePath(DATABASE_NAME).absolutePath)?.database?.close()
            }
        }
        internal fun clearForTests(context: Context) {
            closeForTests(context)
            context.deleteDatabase(DATABASE_NAME)
            context.filesDir.listFiles()?.filter { it.name.startsWith(LEGACY_FILE) }?.forEach { it.delete() }
        }

        internal suspend fun quarantineLegacyState(context: Context) = withContext(Dispatchers.IO) {
            val holder = handle(context)
            holder.initialization.withLock {
                val source = File(context.filesDir, LEGACY_FILE)
                val bytes = source.readBytes()
                try {
                    JSONObject(bytes.toString(Charsets.UTF_8))
                    throw IOException("Legacy state is readable; recovery refused")
                } catch (
                    _: org.json.JSONException,
                ) { }
                check(holder.database.typedDao().metadata(LEGACY_IMPORT) == null) { "Legacy state already imported" }
                val directory = File(context.filesDir, "studio-state-recovery")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot preserve local state")
                val bundle = File(directory, UUID.randomUUID().toString())
                if (!bundle.mkdir()) throw IOException("Cannot create private recovery bundle")
                val temporary = File(bundle, "state.new")
                try {
                    temporary.outputStream().use {
                        it.write(bytes)
                        it.fd.sync()
                    }
                    atomicMove(temporary, File(bundle, "state.json"))
                    listOf("recording-staging", "references").forEach { name ->
                        val audio = File(context.filesDir, name)
                        if (audio.exists()) {
                            if (audio.canonicalFile.parentFile !=
                                context.filesDir.canonicalFile
                            ) {
                                throw IOException("Cannot preserve audio outside private storage")
                            }
                            atomicMove(audio, File(bundle, name))
                        }
                    }
                    if (!source.delete()) throw IOException("Cannot reset legacy state; original retained")
                    File(context.filesDir, LEGACY_FILE + ".new").delete()
                } finally {
                    temporary.delete()
                }
            }
        }

        private fun atomicMove(source: File, target: File) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (
                _: AtomicMoveNotSupportedException,
            ) {
                Files.move(source.toPath(), target.toPath())
            }
        }
    }
}

internal fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.stringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotEmpty)
