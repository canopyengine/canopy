package canopy.graphics.nodes.animation

import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import canopy.core.nodes.types.empty.EmptyNode
import canopy.graphics.nodes.animation.tracks.ActionTrack
import canopy.graphics.nodes.animation.tracks.PropertyTrack
import canopy.utils.UnstableApi
import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore
@UnstableApi
class AnimationTests {
    val sceneManager by lazy { ManagersRegistry.get(SceneManager::class) }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ManagersRegistry.register(
                SceneManager {
                    // AnimationSystem()
                },
            )
        }
    }

    @Test
    fun `animation structure should work`() {
        val emptyNode = EmptyNode("node")

        val animation =
            Animation("anim", 1.5f) {
                PropertyTrack(emptyNode::position) {
                    key(0.5f, Vector2(0f, 0f))
                }
            }

        assertEquals(1, animation.tracks.size)
        assertEquals(1, animation.tracks[0].keyCount)
    }

    @Test
    fun `test animation`() {
        val emptyNode =
            EmptyNode("node") {
                AnimationPlayer("player")
            }
        var counter = 0

        val animation =
            Animation("anim", 1.5f, PlayMode.NORMAL) {
                ActionTrack {
                    key(0.5f) { counter++ }
                    key(1f) { counter++ }
                }
            }

        val animationPlayer = emptyNode.getNode<AnimationPlayer>("player")
        animationPlayer.addAnimation(animation)
        animationPlayer.play(animation.name)

        assertEquals(1, animation.tracks.size)
        assertEquals(2, animation.tracks[0].keyCount)

        sceneManager.currScene = emptyNode

        // Timed action
        var elapsed = 0f
        val delta = 0.1f
        while (elapsed < animation.length) {
            sceneManager.tick(delta)
            elapsed += delta

            Thread.sleep((delta * 100).toLong())
        }
        assertEquals(2, counter)
    }

    @Test
    fun `test adding key outside of defined length`() {
        assertThrows<IllegalArgumentException> {
            Animation("anim", 1.5f) {
                ActionTrack {
                    key(3f) {}
                }
            }
        }
    }
}
