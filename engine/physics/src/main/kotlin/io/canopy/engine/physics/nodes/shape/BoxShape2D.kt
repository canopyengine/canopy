package io.canopy.engine.physics.nodes.shape

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Filter
import io.canopy.engine.core.nodes.core.Node
import ktx.box2d.box

class BoxShape2D(
    /** Width in pixels */
    val width: Float = 1f,
    /** Height in pixels */
    val height: Float = 1f,
) : PhysicsShape2D {
    override fun shapeFactory(
        fixtureNode: Node<*>,
        body: Body,
        position: Vector2,
        angle: Float,
        friction: Float,
        restitution: Float,
        density: Float,
        filter: Filter,
        isSensor: Boolean,
    ) = body.box(
        width,
        height,
        position, // NOW comes from the fixture node
        angle // NOW comes from the fixture node
    ) {
        this.friction = friction
        this.restitution = restitution
        this.density = density
        this.isSensor = isSensor

        // Copy filter
        this.filter.categoryBits = filter.categoryBits
        this.filter.maskBits = filter.maskBits
        this.filter.groupIndex = filter.groupIndex
        this.userData = fixtureNode
    }
}
