package canopy.core.backend

interface CanopyBackendHandle : AutoCloseable {
    fun exit()

    override fun close() = exit()
}
