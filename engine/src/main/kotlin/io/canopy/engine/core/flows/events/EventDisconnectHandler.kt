package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.NodeLifetime

/** A retained handle keeps its listener alive only until any disconnection path executes. */
class EventDisconnectHandler(disconnectHandler: () -> Unit, listener: Any? = null) {
    private var disconnectHandler: (() -> Unit)? = disconnectHandler
    private var listener: Any? = listener
    private var cancelOwnership: (() -> Unit)? = null
    internal fun owned(): EventDisconnectHandler {
        try {
            cancelOwnership = NodeLifetime.own(::disconnect)
        } catch (error: Throwable) {
            disconnect()
            throw error
        }
        return this
    }

    /** Disconnects once and releases the callback and node ownership registration. */
    fun disconnect() {
        val action = disconnectHandler ?: return
        disconnectHandler = null
        listener = null
        try {
            action()
        } finally {
            cancelOwnership?.invoke()
            cancelOwnership = null
        }
    }
}
