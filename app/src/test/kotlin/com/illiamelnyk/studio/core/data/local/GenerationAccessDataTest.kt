package com.illiamelnyk.studio.core.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.data.network.toSession
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GenerationAccessDataTest {
    private lateinit var context: Context

    @Before fun clean() {
        context = RuntimeEnvironment.getApplication()
        StudioStore.clearForTests(context)
    }

    @Test fun missingServerCapabilityDefaultsToLegacyAccessWhileExplicitFalseIsPreserved() = runBlocking {
        server().use { server ->
            val api = api(server)
            server.enqueue(MockResponse().setBody("""{"id":"owner"}"""))
            assertTrue(api.getProfile("dev:fixture").toSession().user.canGenerate)
            server.enqueue(MockResponse().setBody("""{"id":"owner","canGenerate":false}"""))
            assertFalse(api.getProfile("dev:fixture").toSession().user.canGenerate)
        }
    }

    @Test fun explicitDenialAndKnownAdmissionErrorsSurviveTypedRoomCloseAndRestart() = runBlocking {
        val store = StudioStore(context)
        val cached = account(false).copy(
            tracks = listOf(
                DemoCatalog.tracks.first().copy(status = TrackStatus.Failed(AppError.GenerationNotAllowed)),
                DemoCatalog.tracks.last().copy(status = TrackStatus.Failed(AppError.ServiceDailyLimit)),
            ),
        )
        store.saveAccount(cached)
        StudioStore.closeForTests(context)
        val restored = StudioStore(context).readActive().account!!
        assertFalse(restored.session.user.canGenerate)
        assertEquals(cached, restored)
        assertEquals(AppError.GenerationNotAllowed, (restored.tracks.first().status as TrackStatus.Failed).error)
        assertEquals(AppError.ServiceDailyLimit, (restored.tracks.last().status as TrackStatus.Failed).error)
    }

    @Test fun legacyProfileDenialSurvivesJsonImportWhileMissingCapabilityKeepsOldBehavior() = runBlocking {
        val denied = AccountJsonCodec.encode(account(false))
        assertFalse(denied.getJSONObject("profile").getBoolean("canGenerate"))
        val allowed = AccountJsonCodec.encode(account(true, "older-owner"))
        allowed.getJSONObject("profile").remove("canGenerate")
        val root = JSONObject().put("account-owner", denied).put("account-older-owner", allowed).put("active", "owner")
        File(context.filesDir, StudioStore.LEGACY_FILE).writeText(root.toString())
        val store = StudioStore(context)
        assertFalse(store.readActive().account!!.session.user.canGenerate)
        assertTrue(store.readAccount("older-owner")!!.session.user.canGenerate)
        listOf(AppError.GenerationNotAllowed, AppError.ServiceDailyLimit).forEach { error ->
            val failed = DemoCatalog.tracks.first().copy(status = TrackStatus.Failed(error))
            assertEquals(failed.status, failed.toJson().toTrack().status)
        }
    }

    @Test fun cachedDenialBlocksGenerationLocallyWithoutCreatingAPendingAttemptOrRequest() = runBlocking {
        server().use { server ->
            val store = StudioStore(context)
            store.saveAccount(account(false))
            val runtime = runtime(store, api(server))
            val result = DurableTrackRepository(
                runtime,
            ).create(GenerationRequest("Warm cinematic piano and strings"), 1)
            assertEquals(Outcome.Failure(AppError.GenerationNotAllowed), result)
            assertEquals(0, server.requestCount)
            assertNull(store.generationAttempt("owner"))
        }
    }

    @Test fun permission403IsKnownDenialCachesInvitationAndClearsTheUnadmittedAttempt() = runBlocking {
        server().use { server ->
            val store = StudioStore(context)
            store.saveAccount(account(true).copy(token = "dev:fixture"))
            val runtime = runtime(store, api(server))
            val tracks = DurableTrackRepository(runtime)
            server.enqueue(capabilities())
            server.enqueue(denial(403, "GENERATION_NOT_ALLOWED"))
            val request = GenerationRequest("Warm cinematic piano and strings")
            assertEquals(Outcome.Failure(AppError.GenerationNotAllowed), tracks.create(request, 1))
            assertNull(store.generationAttempt("owner"))
            assertFalse(store.readAccount("owner")!!.session.user.canGenerate)
            assertFalse(store.readAccount("owner")!!.requiresAuthentication)
            assertEquals(Outcome.Failure(AppError.GenerationNotAllowed), tracks.create(request, 1))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun service429IsKnownBudgetFailureAndDoesNotTrapTheNextAttemptAsAnUnknownPaidOutcome() = runBlocking {
        server().use { server ->
            val store = StudioStore(context)
            store.saveAccount(account(true).copy(token = "dev:fixture"))
            val runtime = runtime(store, api(server))
            val tracks = DurableTrackRepository(runtime)
            repeat(2) {
                server.enqueue(capabilities())
                server.enqueue(denial(429, "SERVICE_DAILY_LIMIT"))
                assertEquals(
                    Outcome.Failure(AppError.ServiceDailyLimit),
                    tracks.create(
                        GenerationRequest("Warm cinematic piano and strings"),
                        1,
                    ),
                )
                assertNull(store.generationAttempt("owner"))
                assertTrue(store.readAccount("owner")!!.session.user.canGenerate)
                assertFalse(store.readAccount("owner")!!.requiresAuthentication)
            }
            assertEquals(4, server.requestCount)
            assertTrue(store.readAccount("owner")!!.tracks.isEmpty())
        }
    }

    @Test fun schemaOneMigrationRetainsAccountAndPendingIdentityAndDefaultsExistingCapabilityToTrue() = runBlocking {
        withContext(Dispatchers.IO) { createVersionOneDatabase() }
        val store = StudioStore(context)
        val restored = store.readActive()
        assertEquals("previous-version-owner", restored.account!!.session.user.id.value)
        assertTrue(restored.account.session.user.canGenerate)
        assertEquals(7L, restored.revision)
        assertEquals("accepted-before-upgrade", store.generationAttempt("previous-version-owner")!!.key)
        val denied = restored.account.copy(
            session = restored.account.session.copy(
                user = restored.account.session.user.copy(canGenerate = false),
            ),
        )
        store.saveAccount(denied)
        StudioStore.closeForTests(context)
        assertFalse(StudioStore(context).readActive().account!!.session.user.canGenerate)
    }

    @Test fun regularSyncRefreshesActualProfileGrantsAndRevocationsAndPersistsTheLatestPolicy() = runBlocking {
        val store = StudioStore(context)
        val pending = DemoCatalog.tracks.first().copy(status = TrackStatus.Queued, audioUri = null)
        store.saveAccount(account(true).copy(token = "dev:fixture", tracks = listOf(pending)))
        val api = PolicyApi(context, pending.toJson())
        val runtime = runtime(store, api)
        api.allowed = false
        assertTrue(runtime.sync.syncPending("owner") is Outcome.Success)
        assertFalse(store.readActive().account!!.session.user.canGenerate)
        assertFalse(runtime.state.value!!.session.user.canGenerate)
        api.allowed = true
        assertTrue(runtime.sync.syncPending("owner") is Outcome.Success)
        assertTrue(store.readActive().account!!.session.user.canGenerate)
        assertEquals(2, api.profileCalls)
        StudioStore.closeForTests(context)
        assertTrue(StudioStore(context).readActive().account!!.session.user.canGenerate)
    }

    @Test fun mismatchedProfileOwnerNeverReplacesTheActiveAccountOrImportsAnotherUsersPolicy() = runBlocking {
        val store = StudioStore(context)
        val pending = DemoCatalog.tracks.first().copy(status = TrackStatus.Queued, audioUri = null)
        store.saveAccount(account(false).copy(token = "dev:fixture", tracks = listOf(pending)))
        val api = PolicyApi(context, pending.toJson()).apply {
            profileOwner = "foreign-owner"
            allowed = true
        }
        val runtime = runtime(store, api)
        assertEquals(Outcome.Failure(AppError.Unauthorized), runtime.sync.syncPending("owner"))
        assertFalse(store.readAccount("owner")!!.session.user.canGenerate)
        assertTrue(store.readAccount("owner")!!.requiresAuthentication)
        assertNull(store.readAccount("foreign-owner"))
        assertEquals("owner", store.readActive().account!!.session.user.id.value)
    }

    @Test fun lostAcceptedResponseThenPolicyDenialKeepsThePaidKeyThroughRestartAndGrant() = runBlocking {
        val store = StudioStore(context)
        store.saveAccount(account(true).copy(token = "dev:fixture"))
        val accepted = DemoCatalog.tracks.first().copy(status = TrackStatus.Queued, audioUri = null).toJson()
        val api = LostAdmissionApi(context, accepted)
        val originalRuntime = runtime(store, api)
        val tracks = DurableTrackRepository(originalRuntime)
        val request = GenerationRequest("Warm cinematic piano and strings")
        assertEquals(Outcome.Failure(AppError.Network), tracks.create(request, 1))
        val acceptedKey = store.generationAttempt("owner")!!.key
        assertEquals(1, api.admissions)
        api.denyAdmission = true
        assertEquals(Outcome.Failure(AppError.GenerationNotAllowed), tracks.create(request, 1))
        assertEquals(acceptedKey, store.generationAttempt("owner")!!.key)
        assertFalse(store.readAccount("owner")!!.session.user.canGenerate)
        StudioStore.closeForTests(context)
        val restartedStore = StudioStore(context)
        val restartedRuntime = runtime(restartedStore, api)
        api.denyAdmission = false
        api.allowed = true
        assertTrue(restartedRuntime.operation { restartedRuntime.refresh() } is Outcome.Success)
        assertTrue(restartedStore.readAccount("owner")!!.session.user.canGenerate)
        assertTrue(DurableTrackRepository(restartedRuntime).create(request, 1) is Outcome.Success)
        assertEquals(listOf(acceptedKey, acceptedKey, acceptedKey), api.submittedKeys)
        assertEquals(1, api.admissions)
        assertNull(restartedStore.generationAttempt("owner"))
    }

    private class LostAdmissionApi(context: Context, private val accepted: JSONObject) : PolicyApi(context, accepted) {
        var denyAdmission = false
        var admissions = 0
        val submittedKeys = mutableListOf<String>()
        private val admittedKeys = mutableSetOf<String>()
        private var loseFirstResponse = true
        override suspend fun requestFixture(
            method: String,
            path: String,
            token: String,
            body: JSONObject?,
            key: String?,
        ): JSONObject {
            if (path == "/v1/capabilities") {
                return JSONObject()
                    .put("provider", "mock").put("qualityModes", JSONArray().put("auto"))
                    .put("customLyrics", true).put("variants", JSONArray().put(1).put(2))
                    .put("audioReferences", "mock").put("liveCatalogRemix", false).put("maxRemixSources", 4)
            }
            if (path == "/v1/tracks" && method == "POST") {
                val submitted = requireNotNull(key)
                submittedKeys += submitted
                if (denyAdmission) throw ApiFailure(AppError.GenerationNotAllowed, "GENERATION_NOT_ALLOWED")
                if (admittedKeys.add(submitted)) admissions++
                if (loseFirstResponse) {
                    loseFirstResponse = false
                    throw ApiFailure(AppError.Network)
                }
                return accepted
            }
            return super.requestFixture(method, path, token, body, key)
        }
    }

    private open class PolicyApi(context: Context, private val track: JSONObject) : JsonFixtureStudioApi(context) {
        var allowed = true
        var profileOwner = "owner"
        var profileCalls = 0
        override suspend fun requestFixture(
            method: String,
            path: String,
            token: String,
            body: JSONObject?,
            key: String?,
        ): JSONObject = when {
            path == "/v1/me" -> {
                profileCalls++
                JSONObject().put("id", profileOwner).put("canGenerate", allowed)
            }

            path == "/v1/me/quota" -> JSONObject().put("limit", 25).put("remaining", 25)

            path.startsWith("/v1/tracks?") -> JSONObject().put("tracks", JSONArray().put(track))

            path == "/v1/likes" -> JSONObject().put("trackIds", JSONArray())

            path == "/v1/playlists" -> JSONObject().put("playlists", JSONArray())

            path == "/v1/recordings" -> JSONObject().put("recordings", JSONArray())

            else -> throw AssertionError("Unexpected fixture endpoint $method $path")
        }
    }

    private fun createVersionOneDatabase() {
        val relative = "schemas/com.illiamelnyk.studio.core.data.local.database.StudioDatabase/1.json"
        val schemaFile = listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: throw AssertionError("Committed Room schema 1 is required to verify migration")
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(StudioStore.DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        schema.getJSONArray("entities").objects().forEach { entity ->
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            entity.array("indices").objects().forEach {
                                db.execSQL(it.getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        schema.getJSONArray("setupQueries").strings().forEach(db::execSQL)
                        db.execSQL(
                            """
                            INSERT INTO studio_accounts (
                                ownerId, emailVerified, signInMethod, ageConfirmed, marketingOptIn,
                                missingOnboarding, currentTermsVersion, dataMode, remainingCredits,
                                dailyLimit, creditDay, requiresAuthentication
                            ) VALUES ('previous-version-owner', 0, 'Email', 0, 0, '[]', '2026-10-14',
                                'LocalDemo', 25, 25, '2026-10-22', 0)
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "INSERT INTO studio_active_owner (id,ownerId,revision) VALUES (0,'previous-version-owner',7)",
                        )
                        db.execSQL(
                            """
                            INSERT INTO studio_generation_attempts (
                                ownerId, attemptKey, variants, prompt, model, quality, instrumental, remixTrackIds, blocked
                            ) VALUES ('previous-version-owner','accepted-before-upgrade',1,'Warm cinematic strings',
                                'V2_5','Auto',0,'[]',0)
                            """.trimIndent(),
                        )
                        db.execSQL("INSERT INTO store_metadata (`key`,value) VALUES ('legacy_json_import','1')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        throw AssertionError("The fixture must create schema 1 directly")
                }).build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun account(allowed: Boolean, owner: String = "owner") = AccountSnapshot(
        JSONObject().put("id", owner).put("canGenerate", allowed).toSession(),
        token = null,
    )
    private fun runtime(store: StudioStore, api: StudioApi) = StudioAccountRuntime(
        context,
        store,
        api,
        Dispatchers.IO,
        GenerationWorkScheduler.None,
        GenerationNotifications.None,
    )
    private fun server() = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
    private fun api(server: MockWebServer) = StudioApi(
        context,
        ApiConfiguration(
            server.url("/").newBuilder().host("127.0.0.1").build().toString(),
            ApiAuthMode.Development,
            true,
        ),
    )
    private fun capabilities() = MockResponse().setBody(
        """
        {"provider":"mock","qualityModes":["auto"],"customLyrics":true,"variants":[1,2],
        "audioReferences":"mock","liveCatalogRemix":false,"maxRemixSources":4}
        """.trimIndent(),
    )
    private fun denial(status: Int, code: String) = MockResponse().setResponseCode(status)
        .setBody("""{"error":{"code":"$code","details":{"resetsAt":"2026-10-23T00:00:00Z"}}}""")
}
