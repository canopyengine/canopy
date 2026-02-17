package canopy.app.backends

interface CanopyBackendHandle : AutoCloseable {
    fun exit()

    override fun close() = exit()
}
