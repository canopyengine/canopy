package canopy.graphics.nodes.animation

import canopy.backends.test.TestHeadlessCanopyGame
import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import canopy.core.nodes.types.empty.EmptyNode
import canopy.data.assets.AssetsManager
import canopy.graphics.nodes.animation.tracks.ActionTrack
import canopy.graphics.nodes.animation.tracks.SpriteTrack
import canopy.graphics.nodes.visual.AnimatedSprite2D
import canopy.graphics.systems.AnimationSystem
import canopy.utils.UnstableApi
import com.badlogic.gdx.graphics.g2d.TextureRegion
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals

@Ignore
@UnstableApi
class SpriteAnimationTests {
    val assetsManager by lazy { ManagersRegistry.get(AssetsManager::class) }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ManagersRegistry.apply {
                // Scene Manager
                register(
                    SceneManager {
                        AnimationSystem()
                    },
                )

                register(AssetsManager())
            }
        }

        @BeforeAll
        @JvmStatic
        fun setupHeadlessApplication() {
            TestHeadlessCanopyGame().launch()
        }
    }

    @Test
    fun `should create empty animation`() {
        val emptyNode =
            EmptyNode("root") {
                AnimatedSprite2D<TextureRegion>("sprite")
                AnimationPlayer("player")
            }

        val frames =
            arrayOf(
                TextureRegion(),
                TextureRegion(),
                TextureRegion(),
            )

        val executedFrames = mutableListOf<Int>()

        val animation =
            Animation("animation", 1f) {
                val animatedSprite =
                    emptyNode.getNode<AnimatedSprite2D<TextureRegion>>("sprite")

                SpriteTrack(animatedSprite, frames) {
                    keyFrame(0.2f, 0)
                    keyFrame(0.5f, 1)
                    keyFrame(1f, 2)
                }

                ActionTrack {
                    key(0f) {
                        executedFrames += frames.indexOf(animatedSprite.currFrame)
                    }
                    key(0.5f) {
                        executedFrames += frames.indexOf(animatedSprite.currFrame)
                    }
                    key(1f) {
                        executedFrames += frames.indexOf(animatedSprite.currFrame)
                    }
                }
            }

        val animationPlayer =
            emptyNode.getNode<AnimationPlayer>("player")

        animationPlayer.addAnimation(animation)

        animationPlayer.play("animation")

        val sceneManager = ManagersRegistry.get(SceneManager::class)
        val animationSystem = sceneManager.getSystem(AnimationSystem::class)

        // 🔑 Drive time forward
        repeat(11) {
            animationSystem.tick(0.1f)
        }

        // ✅ Assert synchronously
        assertContentEquals(listOf(0, 1, 2), executedFrames)
    }
}
