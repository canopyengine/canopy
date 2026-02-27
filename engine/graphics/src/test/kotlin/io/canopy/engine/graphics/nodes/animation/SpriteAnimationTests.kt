package io.canopy.engine.graphics.nodes.animation

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.canopy.engine.app.test.testHeadlessApp
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.managers.treeSystem
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.data.core.assets.AssetsManager
import io.canopy.engine.graphics.nodes.animation.tracks.ActionTrack
import io.canopy.engine.graphics.nodes.animation.tracks.SpriteTrack
import io.canopy.engine.graphics.nodes.visual.AnimatedSprite2D
import io.canopy.engine.graphics.systems.AnimationSystem
import io.canopy.engine.utils.UnstableApi
import org.junit.jupiter.api.BeforeAll

@Ignore
@UnstableApi
class SpriteAnimationTests {
    val assetsManager by lazyManager<AssetsManager>()

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupHeadlessApplication() {
            testHeadlessApp {
                sceneManager {
                    +AnimationSystem()
                }
                onCreate {
                    ManagersRegistry.register(AssetsManager())
                }
            }.launch()
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
                TextureRegion()
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

        val animationSystem = treeSystem<AnimationSystem>()

        // 🔑 Drive time forward
        repeat(11) {
            animationSystem.tick(0.1f)
        }

        // ✅ Assert synchronously
        assertContentEquals(listOf(0, 1, 2), executedFrames)
    }
}
