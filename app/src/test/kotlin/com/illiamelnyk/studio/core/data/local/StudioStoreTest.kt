package com.illiamelnyk.studio.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteException
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StudioStoreTest {
    private lateinit var context: Context

    private fun initializedStore() = StudioStore(context).also { store ->
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { store.initialize() }
    }

    @Before fun clean() {
        context = RuntimeEnvironment.getApplication()
        StudioStore.clearForTests(context)
        File(context.filesDir, "studio-state-recovery").let { directory ->
            if (directory.isDirectory) directory.walkBottomUp().forEach { it.delete() } else directory.delete()
        }
    }

    @Test fun userSelectedRecoveryPreservesOriginalBytesAndAudioBeforeStartingFreshCache() {
        val original = "{unreadable-private-state".toByteArray()
        val source = File(context.filesDir, StudioStore.LEGACY_FILE).apply { writeBytes(original) }
        val audio = File(context.filesDir, "recovery-private-recording").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val staged = File(context.filesDir, "recording-staging/owner/audio.wav").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val reference = File(context.filesDir, "references/owner/reference.wav").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(7, 8, 9))
        }
        try {
            initializedStore()
            fail("Expected recoverable migration failure")
        } catch (
            _: LegacyStudioStateCorruptedException,
        ) {
            Unit
        }
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { StudioStore.quarantineLegacyState(context) }
        assertFalse(source.exists())
        val backup = File(context.filesDir, "studio-state-recovery").listFiles()!!.single()
        assertArrayEquals(original, File(backup, "state.json").readBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), File(backup, "recording-staging/owner/audio.wav").readBytes())
        assertArrayEquals(byteArrayOf(7, 8, 9), File(backup, "references/owner/reference.wav").readBytes())
        assertFalse(staged.exists())
        assertFalse(reference.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), audio.readBytes())
        assertEquals(0, initializedStore().read().length())
    }

    @Test fun failedPrivatePreservationRetainsOriginalAndDoesNotSetImportMarker() {
        val source = File(context.filesDir, StudioStore.LEGACY_FILE).apply { writeText("{unreadable") }
        try {
            initializedStore()
            fail("Expected recoverable migration failure")
        } catch (
            _: LegacyStudioStateCorruptedException,
        ) {
            Unit
        }
        File(context.filesDir, "studio-state-recovery").writeText("unavailable directory")
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                StudioStore.quarantineLegacyState(context)
            }
            fail("Preservation must fail")
        } catch (
            _: IOException,
        ) {
            Unit
        }
        assertEquals("{unreadable", source.readText())
        try {
            initializedStore()
            fail("Import must remain retryable")
        } catch (
            _: LegacyStudioStateCorruptedException,
        ) {
            Unit
        }
    }

    @Test fun recoveryRefusesAReadableLegacyJournal() {
        val source = File(context.filesDir, StudioStore.LEGACY_FILE).apply {
            writeText("{\"pending-owner\":{\"key\":\"keep\"}}")
        }
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                StudioStore.quarantineLegacyState(context)
            }
            fail("Readable state must not be reset")
        } catch (
            _: IOException,
        ) {
            Unit
        }
        assertTrue(source.exists())
        // An attempt without a readable request may already have been paid for: block it, never replay.
        val store = initializedStore()
        assertTrue(runBlocking(Dispatchers.IO) { store.generationAttempt("owner") }!!.blocked)
    }

    @Test fun legacyImportMovesOwnersAndJournalsIntoTypedTablesOnce() {
        val original = JSONObject()
            .put("active", "owner-a")
            .put("account-owner-a", account("owner-a"))
            .put("account-owner-b", account("owner-b"))
            .put("account-damaged", JSONObject().put("tracks", "damaged"))
            .put("pending-owner-a", JSONObject().put("key", "paid-request-key"))
            .put("deletion-owner-b", JSONObject().put("ownerId", "owner-b").put("localPurged", true))
            .put("recording-pending-hashed-owner-receipt", JSONObject().put("uri", "file:///private/audio"))
            .put("recording-receipt-hashed-owner-receipt", "confirmed-track")
            .put("notification-observed-owner-a", JSONArray().put("track"))
            .put("notification-dirty-owner-a", true)
            .put("retry-track", "replacement")
            .put("request-track", JSONObject().put("prompt", "keep"))
            .put("upload-owner-a-hash", "upload-id")
            .put("future-format", JSONArray().put(JSONObject.NULL).put(42))
        val legacy = File(context.filesDir, StudioStore.LEGACY_FILE).apply { writeText(original.toString()) }
        assertImported(initializedStore())
        assertFalse(legacy.exists())
        StudioStore.closeForTests(context)
        assertImported(initializedStore())
    }

    private fun assertImported(store: StudioStore) = runBlocking(Dispatchers.IO) {
        val dao = store.database.typedDao()
        val accounts = dao.accounts().associateBy { it.account.ownerId }
        // The damaged account cannot be decoded, so it is skipped instead of failing the whole import.
        assertEquals(setOf("owner-a", "owner-b"), accounts.keys)
        accounts.values.forEach { account ->
            assertEquals(1, account.tracks.size)
            assertEquals(1, account.playlists.size)
            assertEquals(1, account.members.size)
            assertEquals(1, account.likes.size)
        }
        assertEquals("owner-a", dao.activeAccount()?.active?.ownerId)
        assertTrue(requireNotNull(dao.attempt("owner-a")).blocked)
        assertTrue(requireNotNull(dao.deletion("owner-b")).localPurged)
        assertEquals("upload-id", dao.upload("owner-a", "hash")?.uploadId)
        assertEquals(listOf("track"), dao.outbox("owner-a")?.observedIds)
        assertEquals(true, dao.outbox("owner-a")?.dirty)
    }

    @Test fun completedImportNeverResurrectsAccountFromLeftoverLegacyFile() {
        val legacy = File(context.filesDir, StudioStore.LEGACY_FILE)
        legacy.writeText(JSONObject().put("account-owner", account("owner")).toString())
        val store = initializedStore()
        store.change { it.remove("account-owner") }
        // Simulate source deletion being interrupted after the import commit.
        legacy.writeText(JSONObject().put("account-owner", account("owner")).toString())
        StudioStore.closeForTests(context)
        assertFalse(initializedStore().read().has("account-owner"))
        assertFalse(legacy.exists())
    }

    @Test fun malformedLegacyJournalFailsClosedAndCanBeRetried() {
        val legacy = File(context.filesDir, StudioStore.LEGACY_FILE).apply { writeText("{broken") }
        try {
            initializedStore()
            fail("Malformed journal must not be treated as an empty migrated account")
        } catch (_: IOException) {
            assertEquals("{broken", legacy.readText())
        }
        legacy.writeText(JSONObject().put("pending-owner", JSONObject().put("key", "same-key")).toString())
        val store = initializedStore()
        assertTrue(runBlocking(Dispatchers.IO) { store.generationAttempt("owner") }!!.blocked)
        assertFalse(legacy.exists())
    }

    @Test fun failedImportRollsBackRowsAndMarkerThenRetriesOriginalJournal() {
        val initialized = initializedStore()
        initialized.withDatabase { it.openHelper.writableDatabase.execSQL("DELETE FROM store_metadata") }
        initialized.failOperationWrites()
        StudioStore.closeForTests(context)
        val original = JSONObject().put("account-owner", account("owner"))
            .put("pending-owner", JSONObject().put("key", "same-key"))
        val legacy = File(context.filesDir, StudioStore.LEGACY_FILE).apply { writeText(original.toString()) }
        try {
            initializedStore()
            fail("Injected SQL failure must abort migration")
        } catch (_: SQLiteException) {
            assertTrue(legacy.exists())
        }
        val retry = StudioStore(context)
        retry.withRawDatabase {
            runBlocking {
                assertTrue(it.typedDao().accounts().isEmpty())
                assertEquals(null, it.typedDao().metadata(StudioStore.LEGACY_IMPORT))
            }
        }
        retry.restoreOperationWrites()
        val imported = initializedStore()
        runBlocking(Dispatchers.IO) {
            assertEquals("owner", imported.readAccount("owner")?.session?.user?.id?.value)
            assertTrue(imported.generationAttempt("owner")!!.blocked)
        }
        assertFalse(legacy.exists())
    }

    @Test fun failedCommitRetainsPreviousAccountActiveOwnerAndPendingOperation() {
        val store = initializedStore()
        val original = JSONObject().put("active", "owner-a").put("account-owner-a", account("owner-a"))
            .put("pending-owner-a", JSONObject().put("key", "original"))
        store.change { root -> original.keys().forEach { root.put(it, original.get(it)) } }
        store.failOperationWrites()
        try {
            store.change {
                it.put("active", "owner-b").put("pending-owner-a", JSONObject().put("key", "changed"))
                    .remove("account-owner-a")
            }
            fail("Commit should fail")
        } catch (_: SQLiteException) {
            assertJsonEquals(original, store.read())
        }
        store.restoreOperationWrites()
        StudioStore.closeForTests(context)
        assertJsonEquals(original, initializedStore().read())
    }

    @Test fun independentInstancesPreserveOtherOwnersDuringConcurrentTypedSaves() {
        val first = initializedStore()
        val second = initializedStore()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..40).map { index ->
                pool.submit {
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        val owner = "owner-" + index
                        (if (index % 2 == 0) first else second).saveAccount(
                            requireNotNull(AccountJsonCodec.decodeCached(owner, account(owner))),
                        )
                    }
                }
            }
            tasks.forEach { it.get() }
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                (1..40).forEach { index ->
                    val owner = "owner-" + index
                    assertEquals(owner, first.readAccount(owner)?.session?.user?.id?.value)
                    assertEquals(owner, second.readAccount(owner)?.session?.user?.id?.value)
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun accountRemovalCascadesCollectionsButRetainsDeletionJournalAndOtherOwner() {
        val store = initializedStore()
        store.change { root ->
            root.put("account-a", account("a")).put("account-b", account("b"))
                .put("deletion-a", JSONObject().put("ownerId", "a")).put("active", "b")
        }
        store.change { it.remove("account-a") }
        listOf("studio_tracks", "studio_playlists", "studio_playlist_tracks", "studio_likes").forEach { table ->
            store.withRawDatabase { db ->
                db.openHelper.writableDatabase.query("SELECT DISTINCT ownerId FROM $table").use { cursor ->
                    assertTrue(table, cursor.moveToFirst())
                    assertEquals(table, "b", cursor.getString(0))
                    assertFalse(table, cursor.moveToNext())
                }
            }
        }
        runBlocking(Dispatchers.IO) {
            val dao = store.database.typedDao()
            assertEquals(listOf("b"), dao.accounts().map { it.account.ownerId })
            assertNotNull(dao.deletion("a"))
        }
        assertNotNull(store.read().getJSONObject("deletion-a"))
        assertEquals("b", store.read().getString("active"))
    }

    private fun account(owner: String): JSONObject {
        val track = DemoCatalog.tracks.first().toJson()
        return JSONObject().put("profile", JSONObject().put("id", owner).put("unknownProfile", 42))
            .put("tracks", JSONArray().put(track)).put("likes", JSONArray().put(track.getString("id")))
            .put(
                "playlists",
                JSONArray().put(
                    JSONObject().put("id", "list").put("name", "Saved")
                        .put("tracks", JSONArray().put(track)).put("futurePlaylistProperty", true),
                ),
            )
            .put("remaining", 12).put("limit", 25).put("futureAccountProperty", "retain")
    }

    private fun assertJsonEquals(expected: JSONObject, actual: JSONObject) {
        assertEquals(canonical(expected), canonical(actual))
    }

    private fun canonical(value: Any): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }

        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }

        else -> JSONArray().put(value).toString().removePrefix("[").removeSuffix("]")
    }
}
