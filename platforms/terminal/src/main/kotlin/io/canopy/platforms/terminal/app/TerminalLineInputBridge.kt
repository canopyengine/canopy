package io.canopy.platforms.terminal.app

import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent
import kotlinx.coroutines.CompletableDeferred

/** Publishes a complete fallback submission atomically with its acknowledgement, independent of frame timing. */
internal class TerminalLineInputBridge(private val input: InputManager) {
    private val lock = Any()
    private var pending: CompletableDeferred<Unit>? = null

    /** Switches mode under the publication lock and waits for its first presentation before blocking input. */
    fun preparePresentation(switchMode: () -> Unit): CompletableDeferred<Unit> = synchronized(lock) {
        check(pending == null) { "Previous terminal line has not been processed" }
        switchMode()
        CompletableDeferred<Unit>().also { pending = it }
    }

    fun submit(line: String): CompletableDeferred<Unit> = synchronized(lock) {
        check(pending == null) { "Previous terminal line has not been processed" }
        CompletableDeferred<Unit>().also {
            input.enqueueBatch(
                listOf(TextInputEvent(line), KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
            )
            pending = it
        }
    }

    /** Drains input with atomic publication excluded. Caller completes the acknowledgement only after rendering. */
    fun processEvents(): CompletableDeferred<Unit>? = synchronized(lock) {
        input.processEvents()
        pending.also { pending = null }
    }

    fun cancel() = synchronized(lock) {
        pending?.cancel()
        pending = null
    }
}
