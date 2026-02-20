package canopy.engine.app.desktop

import canopy.engine.app.appkit.CanopyAppConfig
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

class DesktopCanopyAppConfig(
    title: String = "Canopy Desktop Game",
    fps: Int = 60,
    // Implementation props
    val screenWidth: Int = 800,
    val screenHeight: Int = 600,
    val icons: List<String> = listOf("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png"),
    val configure: Lwjgl3ApplicationConfiguration.() -> Unit = {},
) : CanopyAppConfig(title, fps)
