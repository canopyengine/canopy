package canopy.app.backends

import canopy.app.CanopyGame

fun interface CanopyBackend<C : CanopyBackendConfig> {
    fun launch(app: CanopyGame<C>, config: C, vararg args: String): CanopyBackendHandle
}
