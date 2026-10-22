package com.illiamelnyk.studio.core.data.local

/** Real SQLite constraints exercise rollback after deletes and before the transaction can commit. */
internal fun StudioStore.failOperationWrites() = withDatabase {
    listOf(
        "studio_generation_attempts",
        "studio_generation_requests",
        "studio_deletions",
        "studio_recording_drafts",
        "studio_notification_outbox",
    ).forEach { table ->
        it.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_journal_" + table + " BEFORE INSERT ON " + table +
                " BEGIN SELECT RAISE(ABORT, 'injected durable journal failure'); END",
        )
    }
}

internal fun StudioStore.restoreOperationWrites() = withRawDatabase {
    listOf(
        "studio_generation_attempts",
        "studio_generation_requests",
        "studio_deletions",
        "studio_recording_drafts",
        "studio_notification_outbox",
    ).forEach { table ->
        it.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_journal_" + table)
    }
}

internal fun StudioStore.failConfirmedDeletionWrites() = withDatabase {
    it.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER fail_confirmed_deletion BEFORE INSERT ON studio_deletions " +
            "WHEN NEW.remoteConfirmed = 1 " +
            "BEGIN SELECT RAISE(ABORT, 'injected deletion acknowledgement failure'); END",
    )
}
