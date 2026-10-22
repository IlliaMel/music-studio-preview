package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.domain.Outcome
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CachedAccountRecoveryTest {
    private lateinit var context: Context

    @Before fun cleanStore() {
        context = RuntimeEnvironment.getApplication()
        com.illiamelnyk.studio.core.data.local.StudioStore.clearForTests(context)
    }

    @Test fun missingProfileRecoversWithoutRewritingOtherData() = runTest {
        assertInvalidAccountIsRetained(JSONObject())
    }

    @Test fun malformedTrackRecoversWithoutRewritingOtherData() = runTest {
        assertInvalidAccountIsRetained(account().put("tracks", JSONArray().put(JSONObject())))
    }

    @Test fun malformedTrackTimestampRecoversWithoutRewritingOtherData() = runTest {
        val track = DemoCatalog.tracks.first().toJson().put("createdAt", "not-a-timestamp")
        assertInvalidAccountIsRetained(account().put("tracks", JSONArray().put(track)))
    }

    @Test fun malformedPlaylistRecoversWithoutRewritingOtherData() = runTest {
        assertInvalidAccountIsRetained(account().put("playlists", JSONArray().put(JSONObject())))
    }

    @Test fun nonObjectTrackArrayEntriesAreNotSilentlyDiscarded() = runTest {
        assertInvalidAccountIsRetained(account().put("tracks", JSONArray().put(123)))
    }

    @Test fun malformedNestedPlaylistTracksAreRetainedForRecovery() = runTest {
        val playlist = JSONObject().put("id", "playlist").put("name", "Saved").put("tracks", "wrong type")
        assertInvalidAccountIsRetained(account().put("playlists", JSONArray().put(playlist)))
    }

    @Test fun mismatchedCachedOwnerCannotBecomeTheActiveAccount() = runTest {
        assertInvalidAccountIsRetained(account("different-owner"))
    }

    @Test fun damagedInactiveAccountDoesNotPreventRestoringTheValidOwner() = runTest {
        val store = StudioStore(context)
        store.change {
            it.put("active", "valid").put("account-valid", account("valid"))
                .put("account-broken", JSONObject())
        }
        val before = store.read().toString()
        val repository = repository(store)
        assertEquals("valid", repository.session.first()!!.user.id.value)
        assertEquals(before, store.read().toString())
    }

    @Test fun signingInAgainReplacesOnlyTheInvalidAccount() = runTest {
        val store = StudioStore(context)
        store.change {
            it.put("active", "local-demo").put("account-local-demo", JSONObject())
                .put("account-preserved", JSONObject().put("marker", "keep"))
        }
        val repository = repository(store)
        assertNull(repository.session.first())
        assertTrue(repository.signInDemo() is Outcome.Success)
        assertEquals("local-demo", repository.session.first()!!.user.id.value)
        assertEquals("keep", store.read().getJSONObject("account-preserved").getString("marker"))
    }
    private suspend fun TestScope.assertInvalidAccountIsRetained(invalid: JSONObject) {
        val store = StudioStore(context)
        store.change {
            it.put("active", "local-demo").put("account-local-demo", invalid)
                .put("account-preserved", JSONObject().put("marker", "keep"))
                .put("unrelated-journal", JSONObject().put("pending", true))
        }
        val sentinel = File(context.filesDir, "cache-recovery-recording-sentinel")
        sentinel.writeText("recording data")
        val before = store.read().toString()
        val diskBefore = StudioStore(context).read().toString()
        val repository = repository(store)
        assertNull(repository.session.first())
        assertTrue(repository.observeMine().first().isEmpty())
        assertEquals(before, store.read().toString())
        val runtimeJobs = backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
        runtimeJobs.forEach { it.cancel() }
        runtimeJobs.forEach { it.join() }
        StudioStore.closeForTests(context)
        assertEquals(diskBefore, StudioStore(context).read().toString())
        assertEquals("recording data", sentinel.readText())
    }
    private fun TestScope.repository(store: StudioStore) = DurableStudioRepository(
        context,
        store,
        StudioApi(context, ApiConfiguration("", ApiAuthMode.Disabled, true)),
        backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
    private fun account(owner: String = "local-demo") = JSONObject()
        .put("profile", JSONObject().put("id", owner))
        .put("tracks", JSONArray()).put("playlists", JSONArray())
}
