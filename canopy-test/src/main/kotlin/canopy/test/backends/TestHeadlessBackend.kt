package canopy.test.backends

import canopy.app.CanopyGame
import canopy.app.backends.CanopyBackend
import canopy.app.backends.CanopyBackendConfig
import canopy.app.backends.CanopyBackendHandle
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

object TestHeadlessBackend : CanopyBackend<CanopyBackendConfig> {
    override fun launch(
        app: CanopyGame<CanopyBackendConfig>,
        config: CanopyBackendConfig,
        vararg args: String,
    ): CanopyBackendHandle {
        val headless = HeadlessApplication(app, HeadlessApplicationConfiguration())

        return object : CanopyBackendHandle {
            override fun exit() {
                // Stop loop + dispose listener
                headless.exit()
            }
        }
    }
}
