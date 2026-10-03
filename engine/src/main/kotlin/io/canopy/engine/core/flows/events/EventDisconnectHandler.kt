package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.NodeLifetime

/** Subscription handle that retains its callback until disconnection or node removal. */
class EventDisconnectHandler(disconnectHandler: () -> Unit) {
    private var disconnectHandler: (() -> Unit)? = disconnectHandler
    private var cancelOwnership: (() -> Unit)? = null

    internal fun owned(): EventDisconnectHandler {
        cancelOwnership = NodeLifetime.own(::disconnect)
        return this
    }

    /** Removes the connection once and releases any node ownership registration. */
    fun disconnect() {
        val cleanup = disconnectHandler ?: return
        disconnectHandler = null
        try {
            cleanup()
        } finally {
            cancelOwnership?.invoke()
            cancelOwnership = null
        }
    }
}
