package io.canopy.adapters.libgdx.app.headless

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.App
import ktx.app.KtxGame
import ktx.app.KtxScreen

/** Connects LibGDX headless backend callbacks and shutdown controls to the shared application loop. */
object HeadlessHost {

    /** Creates the headless backend; its callbacks drive the application lifecycle. */
    fun launch(app: App<*>) {
        val host = object : KtxGame<KtxScreen>() {
            override fun create() {
                super.create()
                app.engineLoop.enter()
            }

            override fun render() {
                app.engineLoop.update(Gdx.graphics.deltaTime)
            }

            override fun resize(width: Int, height: Int) {
                app.engineLoop.resize(width, height)
            }

            override fun dispose() {
                try {
                    app.engineLoop.exit()
                } finally {
                    super.dispose()
                }
            }
        }

        val headlessRef = AtomicReference<HeadlessApplication?>()
        val shutdownRequested = AtomicBoolean(false)
        app.installBackendHandle(
            requestExit = {
                val gdxApp = Gdx.app
                when {
                    gdxApp != null -> gdxApp.postRunnable { gdxApp.exit() }
                    headlessRef.get() != null -> headlessRef.get()?.exit()
                    else -> shutdownRequested.set(true)
                }
            },
            forceClose = {
                shutdownRequested.set(true)
                headlessRef.get()?.exit()
            }
        )

        val headless = HeadlessApplication(
            host,
            HeadlessApplicationConfiguration()
        )
        headlessRef.set(headless)
        if (shutdownRequested.get()) headless.exit()
    }
}
