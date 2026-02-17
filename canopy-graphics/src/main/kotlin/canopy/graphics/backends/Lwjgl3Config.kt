package canopy.graphics.backends

import canopy.app.backends.CanopyBackendConfig
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.math.Vector2

class Lwjgl3Config(
    size: Vector2 = Vector2(640f, 480f),
    title: String = "Canopy Game",
    val icons: List<String> = listOf("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png"),
    val configure: Lwjgl3ApplicationConfiguration.() -> Unit = {},
) : CanopyBackendConfig(
    width = size.x.toInt(),
    height = size.y.toInt(),
    title = title
)
