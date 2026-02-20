package canopy.engine.physics.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.concurrent.CountDownLatch
import canopy.engine.core.managers.ManagersRegistry
import canopy.engine.core.managers.SceneManager
import canopy.engine.physics.nodes.body.DynamicBody2D
import canopy.engine.physics.nodes.fixture.Area2D
import canopy.engine.physics.nodes.fixture.Collider2D
import canopy.engine.physics.nodes.shape.BoxShape2D
import canopy.engine.physics.nodes.shape.CircleShape2D
import canopy.engine.physics.systems.PhysicsSystem
import canopy.engine.testkit.app.TestHeadlessCanopyApp
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Shape
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertNotNull

class PhysicsBody2DTests {
    companion object {
        private val started = CountDownLatch(1)

        @BeforeAll
        @JvmStatic
        fun setupHeadlessApplication() {
            val sceneManager = SceneManager {
                registerSystem(PhysicsSystem())
            }

            TestHeadlessCanopyApp(
                sceneManager,
                onCreate = {
                    ManagersRegistry
                        .get(SceneManager::class)
                        .getSystem(PhysicsSystem::class)
                        .replaceWorld()

                    started.countDown()
                }
            ).launch()

            started.await()
        }
    }

    @Test
    fun `fixture should add shape`() {
        val tree =
            DynamicBody2D("root") {
                Collider2D(
                    name = "collider",
                    shape = BoxShape2D(),
                    position = Vector2(100f, 100f)
                )
                Area2D(
                    name = "area",
                    shape = CircleShape2D()
                )
            }
        tree.buildTree()

        assertEquals(2, tree.body.fixtureList.size)

        val body = tree.body
        val collider = tree.getNode<Collider2D>("collider")
        val area = tree.getNode<Area2D>("area")

        assertNotNull(collider)
        assertNotNull(area)

        body.fixtureList.forEach { fixture ->
            if (fixture.userData == collider) {
                assertEquals(Shape.Type.Polygon, fixture.type)
            } else {
                assertEquals(Shape.Type.Circle, fixture.type)
            }
        }
    }
}
