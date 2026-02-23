package io.canopy.engine.physics.nodes.shape

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Filter
import com.badlogic.gdx.physics.box2d.Fixture
import io.canopy.engine.core.nodes.core.Node
import ktx.box2d.circle

class CircleShape2D(
    /** Radius in pixels */
    val radius: Float = 1f,
) : PhysicsShape2D {
    override fun shapeFactory(
        fixtureNode: Node<*>,
        body: Body,
        position: Vector2, // comes from fixture node
        angle: Float, // ignored for circles
        friction: Float,
        restitution: Float,
        density: Float,
        filter: Filter,
        isSensor: Boolean,
    ): Fixture =
        body.circle(
            radius,
            position // fixture offset
        ) {
            this.friction = friction
            this.restitution = restitution
            this.density = density
            this.isSensor = isSensor

            // Copy filter data
            this.filter.categoryBits = filter.categoryBits
            this.filter.maskBits = filter.maskBits
            this.filter.groupIndex = filter.groupIndex
            this.userData = fixtureNode
        }
}
