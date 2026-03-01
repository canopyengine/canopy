package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.NodeRef
import io.canopy.engine.core.nodes.core.behavior
import io.canopy.engine.core.nodes.core.nodeRef
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.BeforeAll

class NodeRefTests {
    private class NeedsRef(name: String, val external: NodeRef<*>, block: NeedsRef.() -> Unit = {}) :
        Node<NeedsRef>(name, block)

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ManagersRegistry.withScope {
                register(SceneManager())
            }
        }
    }

    @Test
    fun `should reference node`() {
        var referencedNode: String = ""

        val tree =
            EmptyNode(name = "root") {
                NeedsRef(
                    name = "referrer",
                    external = nodeRef("$/external")
                ) {
                    behavior(
                        onReady = {
                            referencedNode = external.get(this).name
                        }
                    )
                }
                EmptyNode(name = "external")
            }

        manager<SceneManager>().currScene = tree

        assertEquals("external", referencedNode)
    }
}
