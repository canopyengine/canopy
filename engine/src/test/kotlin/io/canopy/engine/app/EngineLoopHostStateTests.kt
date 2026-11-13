package io.canopy.engine.app

import kotlin.test.*

class EngineLoopHostStateTests {
    @Test
    fun `notifications are coalesced by equality serialized with dispatch and failures survive shutdown`() {
        // Arrange
        val failure = IllegalStateException("host callback")
        val transitions = mutableListOf<Pair<HostState, HostState>>()
        lateinit var loop: EngineLoop
        loop = EngineLoop(
            onEnter = {},
            onUpdate = {},
            onPhysicsUpdate = {},
            onResize = { _, _ -> },
            onExit = {},
            onHostStateChanged = { previous, current ->
                transitions += previous to current
                assertFailsWith<IllegalStateException> { loop.update(0f) }
                if (!current.visible) throw failure
            }
        )
        assertFailsWith<IllegalStateException> { loop.hostStateChanged(HostState(focused = false)) }
        loop.enter()

        // Act
        val unfocused = HostState(focused = false)
        loop.hostStateChanged(unfocused)
        loop.hostStateChanged(unfocused)
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> {
                loop.hostStateChanged(unfocused.copy(visible = false))
            }
        )
        loop.exit()

        // Assert
        assertEquals(2, transitions.size)
        assertEquals(HostState() to unfocused, transitions.first())
        assertSame(failure, loop.failure)
        assertFailsWith<IllegalStateException> { loop.hostStateChanged(HostState()) }
    }
}
