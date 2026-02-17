package canopy.backends.gui

import canopy.core.app.CanopyGame
import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import canopy.data.assets.AssetsManager
import canopy.graphics.managers.CameraManager
import com.badlogic.gdx.math.Vector2

class GuiCanopyGame(
    sceneManager: SceneManager = SceneManager(),
    onCreate: (CanopyGame<Lwjgl3Config>) -> Unit = {},
    onResize: (CanopyGame<Lwjgl3Config>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyGame<Lwjgl3Config>) -> Unit = {},
) : CanopyGame<Lwjgl3Config>(
        sceneManager,
        Lwjgl3Backend,
        onCreate,
        onResize,
        onDispose,
    ) {
    override fun create() {
        ManagersRegistry.apply {
            register(CameraManager())
            register(AssetsManager())
        }
        super.create()
    }

    override fun defaultConfig(): Lwjgl3Config =
        Lwjgl3Config(
            size = Vector2(800f, 600f),
            title = "Canopy Game",
        )
}
