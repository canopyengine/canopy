package io.canopy.engine.physics.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Shape
import io.canopy.engine.app.test.testHeadlessApp
import io.canopy.engine.physics.nodes.body.DynamicBody2D
import io.canopy.engine.physics.nodes.fixture.Area2D
import io.canopy.engine.physics.nodes.fixture.Collider2D
import io.canopy.engine.physics.nodes.shape.BoxShape2D
import io.canopy.engine.physics.nodes.shape.CircleShape2D
import io.canopy.engine.physics.systems.PhysicsSystem
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertNotNull

class PhysicsBody2DTests {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupHeadlessApplication() {
            testHeadlessApp {
                sceneManager {
                    +PhysicsSystem()
                }

                onCreate {
                    sceneManager.getSystem(PhysicsSystem::class).replaceWorld()
                }
            }.launchAsync()
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
