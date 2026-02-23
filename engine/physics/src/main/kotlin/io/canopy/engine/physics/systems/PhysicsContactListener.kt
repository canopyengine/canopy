package io.canopy.engine.physics.systems

import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.physics.nodes.fixture.Area2D
import io.canopy.engine.physics.nodes.fixture.Collider2D

class PhysicsContactListener : ContactListener {
    override fun beginContact(contact: Contact) {
        val fixtureA = contact.fixtureA
        val fixtureB = contact.fixtureB

        val nodeA = fixtureA.userData as? Node<*>
        val nodeB = fixtureB.userData as? Node<*>

        // Process callback
        if (nodeA == null || nodeB == null) return

        when {
            nodeA is Collider2D && nodeB is Collider2D -> {
                nodeA.bodyEntered.emit(nodeB)
                nodeB.bodyEntered.emit(nodeA)
            }

            nodeA is Area2D && nodeB is Collider2D -> nodeA.bodyEntered.emit(nodeB)
            nodeB is Area2D && nodeA is Collider2D -> nodeB.bodyEntered.emit(nodeA)

            nodeA is Area2D && nodeB is Area2D -> {
                nodeA.areaEntered.emit(nodeB)
                nodeB.areaEntered.emit(nodeA)
            }
        }
    }

    override fun endContact(contact: Contact) {
        val fixtureA = contact.fixtureA
        val fixtureB = contact.fixtureB

        val nodeA = fixtureA.userData as? Node<*>
        val nodeB = fixtureB.userData as? Node<*>

        // Process callback
        if (nodeA == null || nodeB == null) return

        when {
            nodeA is Collider2D && nodeB is Collider2D -> {
                nodeA.bodyExited.emit(nodeB)
                nodeB.bodyExited.emit(nodeA)
            }

            nodeA is Area2D && nodeB is Collider2D -> nodeA.bodyExited.emit(nodeB)
            nodeB is Area2D && nodeA is Collider2D -> nodeB.bodyExited.emit(nodeA)

            nodeA is Area2D && nodeB is Area2D -> {
                nodeA.areaExited.emit(nodeB)
                nodeB.areaExited.emit(nodeA)
            }
        }
    }

    override fun preSolve(contact: Contact?, oldManifold: Manifold?) {
        return // TODO("Not yet implemented")
    }

    override fun postSolve(contact: Contact?, impulse: ContactImpulse?) {
        return // TODO("Not yet implemented")
    }
}
