package io.canopy.engine.logging

import kotlin.test.Test

class CanopyLoggingTests {
    @Test
    fun `init and end logging`() {
        CanopyLogging.init()
        CanopyLogging.end("test_reason")

        CanopyLogging.end("test_error", RuntimeException("test error"))
    }
}
