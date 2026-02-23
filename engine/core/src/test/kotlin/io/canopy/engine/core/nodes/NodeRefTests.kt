package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.core.Behavior
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.NodeRef
import io.canopy.engine.core.nodes.core.behavior
import io.canopy.engine.core.nodes.core.nodeRef
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.BeforeAll

class NodeRefTests {
    private class NeedsRef(
        name: String,
        script: (node: NeedsRef) -> Behavior<NeedsRef>? = { null },
        val external: NodeRef<*>,
        block: Node<*>.() -> Unit = {},
    ) : Node<NeedsRef>(
        name,
        script,
        block = block
    )

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
                    external = nodeRef("$/external"),
                    script =
                    behavior(
                        onReady = {
                            referencedNode = external.get(this).name
                        }
                    )
                )
                EmptyNode(name = "external")
            }

        ManagersRegistry.get(SceneManager::class).currScene = tree

        assertEquals("external", referencedNode)
    }
}
