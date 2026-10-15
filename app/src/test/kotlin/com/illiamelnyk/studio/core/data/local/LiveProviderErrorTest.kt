package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.feature.create.presentation.toCreateUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LiveProviderErrorTest {
    private lateinit var context: Context

    @Before
    fun cleanStore() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.listFiles()?.filter { it.name.startsWith("studio-state-v1.json") }?.forEach { it.delete() }
    }

    @Test
    fun providerFailuresKeepTheirMeaningAcrossCacheRestartAndHideBlindRetry() {
        val errors = mapOf(
            "PROVIDER_OUTCOME_UNKNOWN" to AppError.ProviderOutcomeUnknown,
            "UNSUPPORTED_MODE" to AppError.UnsupportedMode,
            "INVALID_REFERENCE" to AppError.InvalidReference,
        )
        errors.forEach { (code, expected) ->
            assertEquals(expected, apiError(code))
            val track = FakeCatalog.glassHarbor.copy(status = TrackStatus.Failed(expected))
            val restored = JSONObject(track.toJson().toString()).toTrack()
            assertEquals(TrackStatus.Failed(expected), restored.status)
            assertFalse(restored.toCreateUi().canRetry)
        }
    }

    @Test
    fun unknownPaidOutcomeCannotBeRetriedThroughRepositoryAfterRestart() = runTest {
        val store = StudioStore(context)
        val repo = DurableStudioRepository(context, store, StudioApi(context), backgroundScope)
        repo.signInDemo()
        repo.create(GenerationRequest("Ambient strings and gentle piano"), 1)
        store.change { root ->
            root.getJSONObject("account-local-demo").getJSONArray("tracks").getJSONObject(0)
                .put("status", "failed")
                .put("error", JSONObject().put("code", "PROVIDER_OUTCOME_UNKNOWN"))
        }
        val restarted = DurableStudioRepository(context, StudioStore(context), StudioApi(context), backgroundScope)
        val failed = restarted.observeMine().first().single()
        val credits = restarted.credits.first()
        assertEquals(Outcome.Failure(AppError.ProviderOutcomeUnknown), restarted.retry(failed.id))
        assertEquals(credits, restarted.credits.first())
        assertEquals(listOf(failed), restarted.observeMine().first())
        assertFalse(StudioStore(context).read().has("retry-" + failed.id.value))
    }
}
