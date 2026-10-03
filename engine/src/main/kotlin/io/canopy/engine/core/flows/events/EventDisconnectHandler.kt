package io.canopy.engine.core.flows.events

/** Subscription handle that delegates removal to the event connection callback. */
class EventDisconnectHandler(private val disconnectHandler: () -> Unit) {
    /** Invokes the removal callback; repeated calls are delegated without additional guarding. */
    fun disconnect() {
        disconnectHandler()
    }
}
