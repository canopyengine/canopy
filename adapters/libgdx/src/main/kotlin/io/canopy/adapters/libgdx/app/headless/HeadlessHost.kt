package io.canopy.adapters.libgdx.app.headless

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.App
import ktx.app.KtxGame
import ktx.app.KtxScreen

object HeadlessHost {

    fun launch(app: App<*>) {
        val host = object : KtxGame<KtxScreen>() {
            override fun create() {
                super.create()
                app.ready()
            }

            override fun render() {
                app.update(Gdx.graphics.deltaTime)
            }

            override fun dispose() {
                try {
                    app.exit()
                } finally {
                    super.dispose()
                }
            }
        }

        val headless = HeadlessApplication(
            host,
            HeadlessApplicationConfiguration()
        )

        app.installBackendHandle(
            requestExit = {
                val gdxApp = Gdx.app
                if (gdxApp != null) {
                    gdxApp.postRunnable { gdxApp.exit() }
                } else {
                    headless.exit()
                }
            },
            forceClose = {
                headless.exit()
            }
        )
    }
}
