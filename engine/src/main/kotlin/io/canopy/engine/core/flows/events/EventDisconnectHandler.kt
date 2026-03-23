package io.canopy.engine.core.flows.events

class EventDisconnectHandler(private val disconnectHandler: () -> Unit) {
    fun disconnect() {
        disconnectHandler()
    }
}
