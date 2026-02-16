package canopy.backends.gui

import canopy.core.app.CanopyGame
import canopy.core.nodes.SceneManager
import com.badlogic.gdx.math.Vector2

class GuiCanopyGame(
    sceneManager: SceneManager,
    onCreate: (SceneManager) -> Unit = {},
    onResize: (SceneManager, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (SceneManager) -> Unit = {},
) : CanopyGame<Lwjgl3Config>(
        sceneManager,
        Lwjgl3Backend,
        onCreate,
        onResize,
        onDispose,
    ) {
    override fun defaultConfig(): Lwjgl3Config =
        Lwjgl3Config(
            size = Vector2(800f, 600f),
            title = "Canopy Game",
        )
}
