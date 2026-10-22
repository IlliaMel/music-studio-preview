package com.illiamelnyk.studio.core.data.local.database

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** One-time markers, such as the import of the JSON file earlier versions kept the library in. */
@Entity(tableName = "store_metadata")
internal data class StoreMetadataEntity(@PrimaryKey val key: String, val value: String)

@Database(
    entities = [
        StoreMetadataEntity::class,
        StoredAccountEntity::class,
        ActiveOwnerEntity::class,
        StoredOwnedTrackEntity::class,
        StoredPlaylistEntity::class,
        StoredMembershipEntity::class,
        StoredLikeEntity::class,
        GenerationAttemptEntity::class,
        GenerationRequestEntity::class,
        RetryLinkEntity::class,
        ReferenceUploadEntity::class,
        TrackDetailEntity::class,
        DeletionIntentEntity::class,
        RecordingDraftEntity::class,
        RecordingReceiptEntity::class,
        NotificationOutboxEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(StudioTypeConverters::class)
internal abstract class StudioDatabase : RoomDatabase() {
    abstract fun typedDao(): StudioTypedDao
}
