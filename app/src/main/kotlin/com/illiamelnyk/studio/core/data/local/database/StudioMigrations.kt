package com.illiamelnyk.studio.core.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing cached accounts retain legacy server behavior until a profile explicitly denies access. */
internal val GENERATION_PERMISSION_MIGRATION = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE studio_accounts ADD COLUMN canGenerate INTEGER NOT NULL DEFAULT 1")
    }
}
