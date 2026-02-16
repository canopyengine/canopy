package canopy.backends.test

import canopy.core.backend.CanopyBackend
import canopy.core.backend.CanopyBackendConfig
import canopy.core.backend.CanopyBackendHandle
import canopy.core.app.CanopyGame
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

object TestHeadlessBackend : CanopyBackend<CanopyBackendConfig> {
    override fun launch(
        app: CanopyGame<CanopyBackendConfig>,
        config: CanopyBackendConfig,
        vararg args: String,
    ) : CanopyBackendHandle {
       val headless = HeadlessApplication(app, HeadlessApplicationConfiguration())

        return object : CanopyBackendHandle {
            override fun exit() {
                // Stop loop + dispose listener
                headless.exit()
            }
        }
    }
}
