package io.canopy.engine.input

import java.util.ArrayDeque
import io.canopy.engine.app.App
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.managers.managerOrNull
import io.canopy.engine.data.saving.registerSaveModule
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.math.Vector2

/**
 * Bridges backend events to frame input states. Only enqueue and enqueueBatch support concurrent producers;
 * other access belongs to the engine thread.
 */
abstract class InputManager : Manager {

    private val mapper = InputMapper()

    private val _actionStates = mutableMapOf<String, InputState>()

    /** Returns copied mapped states, or an empty map while command editing owns this frame's input. */
    val actionStates get() = if (blocksGameplay) emptyMap() else _actionStates.toMap()

    private fun commandHost(): CommandPromptHost? = managerOrNull<CommandPromptHost>()

    internal val blocksGameplay: Boolean get() = commandHost()?.blocksGameplay == true

    /** Async producers publish through one private monitor; consumers invoke hooks after releasing it. */
    private val queueLock = Any()
    private val eventQueue = ArrayDeque<InputEvent>()

    /**
     * Per-frame snapshot of raw events not consumed by command focus.
     * Populated by processEvents(), consumed by InputSystem to dispatch to nodes.
     * Cleared at the start of each processEvents() call.
     */
    private val _eventsThisFrame = mutableListOf<InputEvent>()
    private var eventsConsumedThisFrame = false

    /** Read-only view of raw events, cleared and repopulated by the next processEvents call. */
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
     * 2. Drains async events into backend state, routing focused commands before the gameplay raw snapshot
     * 3. Recomputes action states
     *
     * Call only on the engine thread. Drains until an empty poll, including events enqueued by callbacks.
     * Later publication waits for another drain. Event callbacks run outside the publication monitor.
     * If a callback throws, that event is already removed and the remaining queue stays pending.
     */
    open fun processEvents() {
        // 1. Clear last frame's raw event snapshot
        _eventsThisFrame.clear()
        eventsConsumedThisFrame = false
        val host = commandHost()
        host?.beginInputFrame()

        // 2. Drain queue → backend state + raw snapshot
        while (true) {
            val event = synchronized(queueLock) { eventQueue.pollFirst() } ?: break
            handleEvent(event)
            if (host?.route(event) != true) _eventsThisFrame += event
        }

        // 3. Recompute action states
        updateActions()
    }

    /**
     * Recomputes all mapped action states for the current frame.
     */
    fun updateActions() {
        mapper.actions.forEach { (action, binds) ->
            val rawPressed = !blocksGameplay && binds.any(::pollPressed)
            val previousState = _actionStates[action] ?: InputState.Released

            val nextState = getNextState(
                previousState = previousState,
                rawState = if (rawPressed) InputState.Pressed else InputState.Released
            )

            _actionStates[action] = nextState
        }
    }

    /** Returns Released for unknown actions and while command editing owns this frame's input. */
    fun getActionState(action: String): InputState =
        if (blocksGameplay) InputState.Released else _actionStates[action] ?: InputState.Released

    /** Returns whether the action is held or was just pressed. */
    fun isActionPressed(action: String): Boolean {
        val state = getActionState(action)
        return state == InputState.Pressed || state == InputState.JustPressed
    }

    /** Returns whether the action transitioned to pressed on the last state update. */
    fun isActionJustPressed(action: String): Boolean = getActionState(action) == InputState.JustPressed

    /** Returns whether the action transitioned to released on the last state update. */
    fun isActionJustReleased(action: String): Boolean = getActionState(action) == InputState.JustReleased

    /** Returns whether the action is released or was just released. */
    fun isActionReleased(action: String): Boolean {
        val state = getActionState(action)
        return state == InputState.Released || state == InputState.JustReleased
    }

    /** Polls a physical binding, suppressed while command editing owns this frame's input. */
    fun isPressed(bind: InputBind): Boolean = !blocksGameplay && pollPressed(bind)

    /** Returns -1 or 1 when only one action is pressed, otherwise zero. */
    fun getAxis(negativeAction: String, positiveAction: String): Float {
        val negativePressed = isActionPressed(negativeAction)
        val positivePressed = isActionPressed(positiveAction)

        return when {
            positivePressed && !negativePressed -> 1f
            negativePressed && !positivePressed -> -1f
            else -> 0f
        }
    }

    /** Combines two digital axes without normalizing diagonal input. */
    fun getInputVector(negativeX: String, positiveX: String, negativeY: String, positiveY: String): Vector2 = Vector2(
        getAxis(negativeX, positiveX),
        getAxis(negativeY, positiveY)
    )

    /** Replaces or appends the supplied bindings; replacement also clears all cached action states. */
    fun mapActions(vararg actions: Pair<String, List<InputBind>>, replace: Boolean = true) {
        mapper.mapActions(*actions, replace = replace)

        if (replace) _actionStates.clear()

        actions.forEach { (action, _) ->
            _actionStates[action] = InputState.Released
        }
    }

    /** Registers one action mapping using replacement behavior. */
    operator fun Pair<String, List<InputBind>>.unaryPlus() {
        mapActions(this)
    }

    /** Removes the named mapping and its cached state. */
    fun unmapAction(action: String) {
        mapper.unmapAction(action)
        _actionStates.remove(action)
    }

    /** Removes all mappings and cached action states. */
    fun clearMappings() {
        mapper.clearMappings()
        _actionStates.clear()
    }

    /** Publishes one event in FIFO order; safe for concurrent producers. */
    fun enqueue(event: InputEvent) {
        synchronized(queueLock) { eventQueue.addLast(event) }
    }

    /**
     * Publishes related events contiguously in FIFO order; safe for concurrent producers.
     * Copies event references in caller-supplied order before acquiring the publication monitor.
     * Keep [events] stable while it is copied. Iteration failure publishes nothing; an empty batch publishes nothing.
     * Competing producers and the consumer cannot observe a partially published batch.
     * Event handling is not transactional: callbacks run per event and may fail after consuming part of a batch.
     */
    fun enqueueBatch(events: Iterable<InputEvent>) {
        val snapshot = events.toList()
        synchronized(queueLock) { eventQueue.addAll(snapshot) }
    }

    /** Registers input mapping serialization with the current SaveManager; loading resets states to Released. */
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
