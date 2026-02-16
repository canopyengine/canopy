package canopy.core.backend

import canopy.core.app.CanopyGame

fun interface CanopyBackend<C : CanopyBackendConfig> {
    fun launch(
        app: CanopyGame<C>,
        config: C,
        vararg args: String,
    ): CanopyBackendHandle
}
