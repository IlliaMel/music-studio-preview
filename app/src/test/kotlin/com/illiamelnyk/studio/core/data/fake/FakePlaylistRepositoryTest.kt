package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlaylistRepositoryTest {
    @Test
    fun `account cleanup clears playlists and their membership`() = runTest {
        val repository = FakePlaylistRepository()
        val playlist = (repository.create("Road trip") as Outcome.Success).value
        repository.addTrack(playlist.id, FakeCatalog.tracks.first())
        repository.clear()
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `create trims name and rejects blank without adding a playlist`() = runTest {
        val repository = FakePlaylistRepository()
        assertTrue(repository.create("   ") is Outcome.Failure)
        assertTrue(repository.observeAll().first().isEmpty())
        repository.create("  Road trip  ")
        assertEquals("Road trip", repository.observeAll().first().single().name)
    }

    @Test
    fun `add is idempotent and remove leaves other tracks in order`() = runTest {
        val repository = FakePlaylistRepository()
        val playlist = (repository.create("Road trip") as Outcome.Success).value
        val first = FakeCatalog.tracks[0]
        val second = FakeCatalog.tracks[1]
        repository.addTrack(playlist.id, first)
        repository.addTrack(playlist.id, first)
        repository.addTrack(playlist.id, second)
        assertEquals(listOf(first, second), repository.observeAll().first().single().tracks)
        repository.removeTrack(playlist.id, first.id)
        assertEquals(listOf(second), repository.observeAll().first().single().tracks)
    }

    @Test
    fun `unknown playlist fails without silently creating one`() = runTest {
        val repository = FakePlaylistRepository()
        assertTrue(repository.addTrack("missing", FakeCatalog.tracks.first()) is Outcome.Failure)
        assertTrue(repository.observeAll().first().isEmpty())
    }
}
