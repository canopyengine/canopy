package io.canopy.engine.input

import java.util.concurrent.ConcurrentLinkedQueue
import io.canopy.engine.app.App
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.data.saving.registerSaveModule
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.math.Vector2

abstract class InputManager : Manager {

    private val mapper = InputMapper()

    private val _actionStates = mutableMapOf<String, InputState>()
    val actionStates get() = _actionStates.toMap()

    /**
     * Async → Sync bridge.
     * Backends enqueue events, engine drains them per frame.
     */
    protected val eventQueue = ConcurrentLinkedQueue<InputEvent>()

    /**
     * Per-frame snapshot of all raw events processed this frame.
     * Populated by processEvents(), consumed by InputSystem to dispatch to nodes.
     * Cleared at the start of each processEvents() call.
     */
    private val _eventsThisFrame = mutableListOf<InputEvent>()
    private var eventsConsumedThisFrame = false
    val eventsThisFrame: List<InputEvent> get() = _eventsThisFrame

    /** Returns raw events once per frame, even if a system ticks multiple times. */
    internal fun consumeEventsThisFrame(): List<InputEvent> {
        if (eventsConsumedThisFrame) return emptyList()
        eventsConsumedThisFrame = true
        return _eventsThisFrame.toList()
    }

    /**
     * Backend-specific polling hook.
     * (Used after events are processed)
     */
    protected abstract fun pollPressed(bind: InputBind): Boolean

    /**
     * Backend-specific event handling hook.
     * Default: no-op (poll-only backends can ignore events)
     */
    protected open fun handleEvent(event: InputEvent) {}

    /**
     * 🔹 MAIN ENTRY POINT (call once per frame)
     *
     * This:
     * 1. Clears last frame's raw event snapshot
     * 2. Drains async events → backend state + raw snapshot
     * 3. Recomputes action states
     */
    open fun processEvents() {
        // 1. Clear last frame's raw event snapshot
        _eventsThisFrame.clear()
        eventsConsumedThisFrame = false

        // 2. Drain queue → backend state + raw snapshot
        while (true) {
            val event = eventQueue.poll() ?: break
            _eventsThisFrame += event
            handleEvent(event)
        }

        // 3. Recompute action states
        updateActions()
    }

    /**
     * Recomputes all mapped action states for the current frame.
     */
    fun updateActions() {
        mapper.actions.forEach { (action, binds) ->
            val rawPressed = binds.any(::pollPressed)
            val previousState = getActionState(action)

            val nextState = getNextState(
                previousState = previousState,
                rawState = if (rawPressed) InputState.Pressed else InputState.Released
            )

            _actionStates[action] = nextState
        }
    }

    fun getActionState(action: String): InputState = _actionStates[action] ?: InputState.Released

    fun isActionPressed(action: String): Boolean {
        val state = getActionState(action)
        return state == InputState.Pressed || state == InputState.JustPressed
    }

    fun isActionJustPressed(action: String): Boolean = getActionState(action) == InputState.JustPressed

    fun isActionJustReleased(action: String): Boolean = getActionState(action) == InputState.JustReleased

    fun isActionReleased(action: String): Boolean {
        val state = getActionState(action)
        return state == InputState.Released || state == InputState.JustReleased
    }

    fun isPressed(bind: InputBind): Boolean = pollPressed(bind)

    fun getAxis(negativeAction: String, positiveAction: String): Float {
        val negativePressed = isActionPressed(negativeAction)
        val positivePressed = isActionPressed(positiveAction)

        return when {
            positivePressed && !negativePressed -> 1f
            negativePressed && !positivePressed -> -1f
            else -> 0f
        }
    }

    fun getInputVector(negativeX: String, positiveX: String, negativeY: String, positiveY: String): Vector2 = Vector2(
        getAxis(negativeX, positiveX),
        getAxis(negativeY, positiveY)
    )

    fun mapActions(vararg actions: Pair<String, List<InputBind>>, replace: Boolean = true) {
        mapper.mapActions(*actions, replace = replace)

        if (replace) _actionStates.clear()

        actions.forEach { (action, _) ->
            _actionStates[action] = InputState.Released
        }
    }

    operator fun Pair<String, List<InputBind>>.unaryPlus() {
        mapActions(this)
    }

    fun unmapAction(action: String) {
        mapper.unmapAction(action)
        _actionStates.remove(action)
    }

    fun clearMappings() {
        mapper.clearMappings()
        _actionStates.clear()
    }

    /**
     * Called by async producers (coroutines, callbacks, etc.)
     */
    fun enqueue(event: InputEvent) {
        eventQueue.add(event)
    }

    fun registerPersistence(destination: String = "input", moduleId: String = "input") {
        registerSaveModule(
            destination = destination,
            id = moduleId,
            serializer = InputData.serializer(),
            onSave = { mapper.toData() },
            onLoad = {
                mapper.loadData(it)
                _actionStates.clear()
                mapper.actions.keys.forEach { action ->
                    _actionStates[action] = InputState.Released
                }
            }
        )
    }

    private fun getNextState(previousState: InputState, rawState: InputState): InputState {
        val previousWasPressed =
            previousState == InputState.Pressed || previousState == InputState.JustPressed

        val previousWasReleased =
            previousState == InputState.Released || previousState == InputState.JustReleased

        return when (rawState) {
            InputState.Pressed ->
                if (previousWasReleased) InputState.JustPressed else InputState.Pressed

            InputState.Released ->
                if (previousWasPressed) InputState.JustReleased else InputState.Released

            else -> InputState.Released
        }
    }
}

/**
 * DSL helper
 */
fun App<*>.inputs(vararg mappings: Pair<String, List<InputBind>>) {
    manager<InputManager>().mapActions(*mappings)
}
