package io.github.canopyengine

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

fun headlessApp() {
    HeadlessApplication(
        object : ApplicationListener {
            override fun create() {}

            override fun resize(
                width: Int,
                height: Int,
            ) {}

            override fun render() {}

            override fun pause() {}

            override fun resume() {}

            override fun dispose() {}
        },
        HeadlessApplicationConfiguration(),
    )
}
