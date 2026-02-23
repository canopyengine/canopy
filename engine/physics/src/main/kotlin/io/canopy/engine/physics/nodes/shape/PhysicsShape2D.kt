package io.canopy.engine.physics.nodes.shape

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Filter
import com.badlogic.gdx.physics.box2d.Fixture
import io.canopy.engine.core.nodes.core.Node

interface PhysicsShape2D {
    fun shapeFactory(
        fixtureNode: Node<*>,
        body: Body,
        position: Vector2,
        angle: Float,
        friction: Float,
        restitution: Float,
        density: Float,
        filter: Filter,
        isSensor: Boolean,
    ): Fixture
}
