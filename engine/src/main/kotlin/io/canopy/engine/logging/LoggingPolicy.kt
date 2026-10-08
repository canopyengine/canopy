package io.canopy.engine.logging

/**
 * Opens logging resources for one application. The host policy leaves backend configuration unchanged.
 * Install custom providers before creating the loggers that should use them; policies never replace providers.
 * A policy must clean up partial resources if [start] throws before returning a session.
 */
fun interface LoggingPolicy {
    /** Opens a new session using the running engine version. */
    fun start(engineVersion: String): LoggingSession

    companion object {
        /** Uses the host's logging backend without files, banners, or backend lifecycle changes. */
        val Host: LoggingPolicy = LoggingPolicy { LoggingSession.Host }
    }
}

/** Logging state owned by one application, independent of other application sessions. */
interface LoggingSession : AutoCloseable {
    /**
     * Calls [block] once on the calling thread with temporary session context, restoring prior context afterward.
     * Context setup may throw before calling [block]; App teardown then attempts cleanup in host context.
     */
    fun <T> withContext(block: () -> T): T = block()

    /** Records the outcome before application exit callbacks; resources remain available until [close]. */
    fun end(reason: String, failure: Throwable?) = Unit

    /** Releases only this session's resources; repeated calls must be harmless. */
    override fun close() = Unit

    companion object {
        /** Stateless session for logging entirely controlled by the host. */
        val Host: LoggingSession = object : LoggingSession {}
    }
}
