package io.canopy.engine.logging.api

/**
 * Logs = entrypoint for obtaining Logger instances.
 *
 * Uses a pluggable provider so the API does not depend on SLF4J directly.
 */
object Logs {
    @Volatile private var provider: LogProvider = DefaultProvider

    fun setProvider(provider: LogProvider) {
        this.provider = provider
    }

    fun get(name: String): Logger = provider.get(name)

    inline fun <reified T : Any> of(): Logger = get(T::class.java.name)
}

inline fun <reified T : Any> logger(): Logger = Logs.of<T>()
