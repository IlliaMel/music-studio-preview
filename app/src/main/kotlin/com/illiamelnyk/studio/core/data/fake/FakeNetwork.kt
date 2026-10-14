package com.illiamelnyk.studio.core.data.fake

import kotlin.time.Duration.Companion.milliseconds

/** How long the fakes pretend a request to the backend takes. */
internal object FakeNetwork {
    val latency = 300.milliseconds
}
