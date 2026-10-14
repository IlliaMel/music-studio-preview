package com.illiamelnyk.studio.feature.create.data.fake

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.Style
import com.illiamelnyk.studio.feature.create.domain.StyleCatalog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * A fixed list of styles, after a short wait like a request.
 *
 * Temporary: replaced by the backend-backed implementation, which lists the service's styles.
 *
 * @param latency how long the "request" takes; tests pass zero.
 */
class FakeStyleCatalog(private val latency: Duration = 300.milliseconds) : StyleCatalog {

    override suspend fun styles(): Outcome<List<Style>> {
        delay(latency)
        return Outcome.Success(STYLES)
    }

    companion object {
        val STYLES: List<Style> = listOf(
            style("lofi", "Lo-fi", "Dusty drums", "Rhodes", "Vinyl"),
            style("synthwave", "Synthwave", "Arpeggios", "Retro", "Night drive"),
            style("indie-folk", "Indie folk", "Acoustic", "Harmonies", "Warm"),
            style("dnb", "Drum and bass", "Breakbeats", "Sub bass", "Fast"),
            style("cinematic", "Cinematic", "Strings", "Choir", "Epic"),
            style("bossa-nova", "Bossa nova", "Nylon guitar", "Brushes", "Soft"),
            style("trap", "Trap", "808s", "Hi-hats", "Dark"),
            style("ambient", "Ambient", "Pads", "Textures", "Slow"),
            style("afrobeats", "Afrobeats", "Percussion", "Groove", "Sunny"),
            style("jazz-trio", "Jazz trio", "Piano", "Upright bass", "Swing"),
        )

        private fun style(id: String, name: String, vararg tags: String) =
            Style(id = id, name = name, tags = tags.toList(), coverSeed = CoverSeed(name))
    }
}
