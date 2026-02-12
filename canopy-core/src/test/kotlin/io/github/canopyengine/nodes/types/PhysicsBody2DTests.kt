package anchors.framework.utils.nodes.types

import anchors.framework.nodes.types.physics.body.DynamicBody2D
import anchors.framework.nodes.types.physics.fixture.Area2D
import anchors.framework.nodes.types.physics.fixture.Collider2D
import anchors.framework.nodes.types.physics.shape.BoxShape2D
import anchors.framework.nodes.types.physics.shape.CircleShape2D
import anchors.framework.systems.physics.PhysicsSystem
import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Shape
import io.github.canopyengine.headlessApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals

class PhysicsBody2DTests {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            val sceneManager =
                SceneManager {
                    PhysicsSystem()
                }
            ManagersRegistry.register(sceneManager)

            sceneManager.setup()

            sceneManager.getSystem(PhysicsSystem::class).replaceWorld()
        }

        @BeforeAll
        @JvmStatic
        fun setupHeadlessApplication() {
            headlessApp()
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
