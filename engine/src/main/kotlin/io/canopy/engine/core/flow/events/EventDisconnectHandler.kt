package io.canopy.engine.core.flow.events

class EventDisconnectHandler(private val disconnectHandler: () -> Unit) {
    fun disconnect() {
        disconnectHandler()
    }
}
