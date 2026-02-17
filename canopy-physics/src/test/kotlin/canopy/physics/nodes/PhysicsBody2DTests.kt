package canopy.physics.nodes

import canopy.backends.test.TestHeadlessCanopyGame
import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import canopy.physics.nodes.body.DynamicBody2D
import canopy.physics.nodes.fixture.Area2D
import canopy.physics.nodes.fixture.Collider2D
import canopy.physics.nodes.shape.BoxShape2D
import canopy.physics.nodes.shape.CircleShape2D
import canopy.physics.systems.PhysicsSystem
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Shape
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertNotNull
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals

class PhysicsBody2DTests {
    companion object {
        private val started = CountDownLatch(1)

        @BeforeAll
        @JvmStatic
        fun setupHeadlessApplication() {
            val sceneManager = SceneManager { PhysicsSystem() }

            TestHeadlessCanopyGame(
                sceneManager,
                onCreate = {
                    ManagersRegistry
                        .get(SceneManager::class)
                        .getSystem(PhysicsSystem::class)
                        .replaceWorld()

                    started.countDown()
                },
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
                    position = Vector2(100f, 100f),
                )
                Area2D(
                    name = "area",
                    shape = CircleShape2D(),
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
