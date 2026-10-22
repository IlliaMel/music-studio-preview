package com.illiamelnyk.studio.core.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.illiamelnyk.studio.core.data.local.DeletionAudio
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "studio_accounts", primaryKeys = ["ownerId"])
internal data class StoredAccountEntity(
    val ownerId: String,
    val email: String?,
    val emailVerified: Boolean,
    val signInMethod: String,
    val displayName: String?,
    val ageConfirmed: Boolean,
    val acceptedTermsVersion: String?,
    val marketingOptIn: Boolean,
    val missingOnboarding: List<String>,
    val currentTermsVersion: String,
    val suggestedDisplayName: String?,
    val dataMode: String,
    val tokenReference: String?,
    val remainingCredits: Int,
    val dailyLimit: Int,
    val creditDay: String,
    val requiresAuthentication: Boolean,
    @ColumnInfo(defaultValue = "1") val canGenerate: Boolean = true,
)

@Entity(tableName = "studio_active_owner", primaryKeys = ["id"])
internal data class ActiveOwnerEntity(val id: Int, val ownerId: String?, val revision: Long)

@Entity(
    tableName = "studio_tracks",
    primaryKeys = ["ownerId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = StoredAccountEntity::class,
            parentColumns = ["ownerId"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class StoredOwnedTrackEntity(
    val ownerId: String,
    val trackId: String,
    val position: Int,
    @Embedded val fields: StoredTrackFields,
)

@Entity(
    tableName = "studio_playlists",
    primaryKeys = ["ownerId", "playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = StoredAccountEntity::class,
            parentColumns = ["ownerId"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class StoredPlaylistEntity(
    val ownerId: String,
    val playlistId: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "studio_playlist_tracks",
    primaryKeys = ["ownerId", "playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = StoredPlaylistEntity::class,
            parentColumns = ["ownerId", "playlistId"],
            childColumns = ["ownerId", "playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class StoredMembershipEntity(
    val ownerId: String,
    val playlistId: String,
    val position: Int,
    val trackId: String,
    @Embedded val fields: StoredTrackFields,
)

@Entity(
    tableName = "studio_likes",
    primaryKeys = ["ownerId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = StoredAccountEntity::class,
            parentColumns = ["ownerId"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class StoredLikeEntity(val ownerId: String, val trackId: String, val position: Int)

@Entity(tableName = "studio_generation_attempts", primaryKeys = ["ownerId"])
internal data class GenerationAttemptEntity(
    val ownerId: String,
    val attemptKey: String,
    val variants: Int,
    @Embedded val fields: StoredRequestFields,
    val blocked: Boolean = false,
)

@Entity(tableName = "studio_generation_requests", primaryKeys = ["ownerId", "trackId"])
internal data class GenerationRequestEntity(
    val trackId: String,
    val ownerId: String,
    @Embedded val fields: StoredRequestFields,
)

@Entity(tableName = "studio_retry_links", primaryKeys = ["ownerId", "originalId"])
internal data class RetryLinkEntity(val originalId: String, val ownerId: String, val replacementId: String)

@Entity(tableName = "studio_reference_uploads", primaryKeys = ["ownerId", "sourceHash"])
internal data class ReferenceUploadEntity(val ownerId: String, val sourceHash: String, val uploadId: String)

@Entity(tableName = "studio_track_details", primaryKeys = ["ownerId", "trackId"])
internal data class TrackDetailEntity(
    val trackId: String,
    val ownerId: String,
    val customLyrics: String?,
    val words: List<StoredWord>,
)

@Entity(tableName = "studio_deletions", primaryKeys = ["ownerId"])
internal data class DeletionIntentEntity(
    val ownerId: String,
    val token: String?,
    val audio: List<DeletionAudio>,
    val references: List<String>,
    val remoteConfirmed: Boolean,
    val localPurged: Boolean,
    val remoteCompletionUnverified: Boolean,
    val requiresReauthentication: Boolean,
)

@Entity(tableName = "studio_recording_drafts", primaryKeys = ["key"])
internal data class RecordingDraftEntity(
    val key: String,
    val ownerId: String?,
    val ownerHash: String,
    val localId: String,
    val uri: String,
    val title: String,
    val durationMs: Long,
    val confirmed: Boolean,
)

@Entity(tableName = "studio_recording_receipts", primaryKeys = ["key"])
internal data class RecordingReceiptEntity(
    val key: String,
    val ownerId: String?,
    val ownerHash: String,
    val trackId: String,
)

@Entity(tableName = "studio_notification_outbox", primaryKeys = ["ownerId"])
internal data class NotificationOutboxEntity(val ownerId: String, val observedIds: List<String>, val dirty: Boolean)

internal data class StoredAccountRelations(
    @Embedded val account: StoredAccountEntity,
    @Relation(parentColumn = "ownerId", entityColumn = "ownerId") val tracks: List<StoredOwnedTrackEntity>,
    @Relation(parentColumn = "ownerId", entityColumn = "ownerId") val playlists: List<StoredPlaylistEntity>,
    @Relation(parentColumn = "ownerId", entityColumn = "ownerId") val members: List<StoredMembershipEntity>,
    @Relation(parentColumn = "ownerId", entityColumn = "ownerId") val likes: List<StoredLikeEntity>,
)

internal data class StoredActiveAccount(
    @Embedded val active: ActiveOwnerEntity,
    @Relation(parentColumn = "ownerId", entityColumn = "ownerId", entity = StoredAccountEntity::class)
    val accounts: List<StoredAccountRelations>,
)

@Dao
internal interface StudioTypedDao {
    @Query("SELECT value FROM store_metadata WHERE `key` = :key")
    suspend fun metadata(key: String): String?

    @Upsert suspend fun putMetadata(value: StoreMetadataEntity)

    @Transaction
    @Query("SELECT * FROM studio_accounts WHERE ownerId = :owner")
    suspend fun account(owner: String): StoredAccountRelations?

    @Transaction
    @Query("SELECT * FROM studio_accounts")
    suspend fun accounts(): List<StoredAccountRelations>

    @Transaction
    @Query("SELECT * FROM studio_active_owner WHERE id = 0")
    suspend fun activeAccount(): StoredActiveAccount?

    @Transaction
    @Query("SELECT * FROM studio_active_owner WHERE id = 0")
    fun observeActiveAccount(): Flow<StoredActiveAccount?>

    @Query("DELETE FROM studio_tracks WHERE ownerId = :owner AND trackId = :id")
    suspend fun deleteTrack(owner: String, id: String)

    @Query("DELETE FROM studio_playlists WHERE ownerId = :owner AND playlistId = :id")
    suspend fun deletePlaylist(owner: String, id: String)

    @Query(
        "DELETE FROM studio_playlist_tracks WHERE ownerId = :owner AND playlistId = :playlist AND position = :position",
    )
    suspend fun deleteMember(owner: String, playlist: String, position: Int)

    @Query("DELETE FROM studio_likes WHERE ownerId = :owner AND trackId = :id")
    suspend fun deleteLike(owner: String, id: String)

    @Upsert suspend fun putStoredAccount(value: StoredAccountEntity)

    @Query("DELETE FROM studio_accounts")
    suspend fun clearStoredAccount()

    @Upsert suspend fun putActiveOwner(value: ActiveOwnerEntity)

    @Query("DELETE FROM studio_active_owner")
    suspend fun clearActiveOwner()

    @Upsert suspend fun putStoredOwnedTrack(value: StoredOwnedTrackEntity)

    @Upsert suspend fun putStoredPlaylist(value: StoredPlaylistEntity)

    @Upsert suspend fun putStoredMembership(value: StoredMembershipEntity)

    @Upsert suspend fun putStoredLike(value: StoredLikeEntity)

    @Upsert suspend fun putGenerationAttempt(value: GenerationAttemptEntity)

    @Query("SELECT * FROM studio_generation_attempts")
    suspend fun allGenerationAttempt(): List<GenerationAttemptEntity>

    @Query("DELETE FROM studio_generation_attempts")
    suspend fun clearGenerationAttempt()

    @Upsert suspend fun putGenerationRequest(value: GenerationRequestEntity)

    @Query("SELECT * FROM studio_generation_requests")
    suspend fun allGenerationRequest(): List<GenerationRequestEntity>

    @Query("DELETE FROM studio_generation_requests")
    suspend fun clearGenerationRequest()

    @Upsert suspend fun putRetryLink(value: RetryLinkEntity)

    @Query("SELECT * FROM studio_retry_links")
    suspend fun allRetryLink(): List<RetryLinkEntity>

    @Query("DELETE FROM studio_retry_links")
    suspend fun clearRetryLink()

    @Upsert suspend fun putReferenceUpload(value: ReferenceUploadEntity)

    @Query("SELECT * FROM studio_reference_uploads")
    suspend fun allReferenceUpload(): List<ReferenceUploadEntity>

    @Query("DELETE FROM studio_reference_uploads")
    suspend fun clearReferenceUpload()

    @Upsert suspend fun putTrackDetail(value: TrackDetailEntity)

    @Query("SELECT * FROM studio_track_details")
    suspend fun allTrackDetail(): List<TrackDetailEntity>

    @Query("DELETE FROM studio_track_details")
    suspend fun clearTrackDetail()

    @Upsert suspend fun putDeletionIntent(value: DeletionIntentEntity)

    @Query("SELECT * FROM studio_deletions")
    suspend fun allDeletionIntent(): List<DeletionIntentEntity>

    @Query("DELETE FROM studio_deletions")
    suspend fun clearDeletionIntent()

    @Upsert suspend fun putRecordingDraft(value: RecordingDraftEntity)

    @Query("SELECT * FROM studio_recording_drafts")
    suspend fun allRecordingDraft(): List<RecordingDraftEntity>

    @Query("DELETE FROM studio_recording_drafts")
    suspend fun clearRecordingDraft()

    @Upsert suspend fun putRecordingReceipt(value: RecordingReceiptEntity)

    @Query("SELECT * FROM studio_recording_receipts")
    suspend fun allRecordingReceipt(): List<RecordingReceiptEntity>

    @Query("DELETE FROM studio_recording_receipts")
    suspend fun clearRecordingReceipt()

    @Upsert suspend fun putNotificationOutbox(value: NotificationOutboxEntity)

    @Query("SELECT * FROM studio_notification_outbox")
    suspend fun allNotificationOutbox(): List<NotificationOutboxEntity>

    @Query("DELETE FROM studio_notification_outbox")
    suspend fun clearNotificationOutbox()

    @Query("DELETE FROM studio_accounts WHERE ownerId = :owner")
    suspend fun deleteAccount(owner: String)

    @Query("SELECT * FROM studio_generation_attempts WHERE ownerId = :owner")
    suspend fun attempt(owner: String): GenerationAttemptEntity?

    @Query("DELETE FROM studio_generation_attempts WHERE ownerId = :owner")
    suspend fun deleteAttempt(owner: String)

    @Query("SELECT * FROM studio_generation_requests WHERE ownerId = :owner AND trackId = :id")
    suspend fun generationRequest(owner: String, id: String): GenerationRequestEntity?

    @Query("DELETE FROM studio_generation_requests WHERE ownerId = :owner AND trackId = :id")
    suspend fun deleteRequest(owner: String, id: String)

    @Query("SELECT * FROM studio_retry_links WHERE ownerId = :owner AND originalId = :id")
    suspend fun retry(owner: String, id: String): RetryLinkEntity?

    @Query("DELETE FROM studio_retry_links WHERE ownerId = :owner AND originalId = :id")
    suspend fun deleteRetry(owner: String, id: String)

    @Query("SELECT * FROM studio_reference_uploads WHERE ownerId = :owner AND sourceHash = :hash")
    suspend fun upload(owner: String, hash: String): ReferenceUploadEntity?

    @Query("SELECT * FROM studio_track_details WHERE ownerId = :owner AND trackId = :id")
    suspend fun detail(owner: String, id: String): TrackDetailEntity?

    @Query("DELETE FROM studio_track_details WHERE ownerId = :owner AND trackId = :id")
    suspend fun deleteDetail(owner: String, id: String)

    @Query("SELECT * FROM studio_deletions WHERE ownerId = :owner")
    suspend fun deletion(owner: String): DeletionIntentEntity?

    @Query("DELETE FROM studio_deletions WHERE ownerId = :owner")
    suspend fun deleteDeletion(owner: String)

    @Query("SELECT * FROM studio_recording_drafts WHERE `key` = :key")
    suspend fun recordingDraft(key: String): RecordingDraftEntity?

    @Query("SELECT * FROM studio_recording_receipts WHERE `key` = :key")
    suspend fun recordingReceipt(key: String): RecordingReceiptEntity?

    @Query("DELETE FROM studio_recording_drafts WHERE `key` = :key")
    suspend fun deleteRecordingDraft(key: String)

    @Query("SELECT * FROM studio_recording_drafts WHERE ownerHash = :hash")
    suspend fun recordingDrafts(hash: String): List<RecordingDraftEntity>

    @Query("DELETE FROM studio_recording_drafts WHERE ownerHash = :hash")
    suspend fun deleteRecordingDrafts(hash: String)

    @Query("DELETE FROM studio_recording_receipts WHERE ownerHash = :hash")
    suspend fun deleteRecordingReceipts(hash: String)

    @Query("DELETE FROM studio_reference_uploads WHERE ownerId = :owner")
    suspend fun deleteUploads(owner: String)

    @Query("SELECT * FROM studio_notification_outbox WHERE ownerId = :owner")
    suspend fun outbox(owner: String): NotificationOutboxEntity?

    @Query("DELETE FROM studio_notification_outbox WHERE ownerId = :owner")
    suspend fun deleteOutbox(owner: String)

    @Query("DELETE FROM studio_generation_requests WHERE ownerId = :owner")
    suspend fun deleteOwnerRequests(owner: String)

    @Query("DELETE FROM studio_retry_links WHERE ownerId = :owner")
    suspend fun deleteOwnerRetries(owner: String)

    @Query("DELETE FROM studio_track_details WHERE ownerId = :owner")
    suspend fun deleteOwnerDetails(owner: String)

    @Query("DELETE FROM studio_generation_attempts WHERE ownerId = :owner AND attemptKey = :key")
    suspend fun deleteAttemptIfCurrent(owner: String, key: String)
}
