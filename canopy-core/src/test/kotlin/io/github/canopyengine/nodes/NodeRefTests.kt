package anchors.framework.utils.nodes

import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import canopy.core.nodes.core.Behavior
import canopy.core.nodes.core.Node
import canopy.core.nodes.core.NodeRef
import canopy.core.nodes.core.behavior
import canopy.core.nodes.core.nodeRef
import canopy.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeRefTests {
    private class NeedsRef(
        name: String,
        script: (node: NeedsRef) -> Behavior<NeedsRef>? = { null },
        val external: NodeRef<*>,
        block: Node<*>.() -> Unit = {},
    ) : Node<NeedsRef>(
            name,
            script,
            block = block,
        )

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ManagersRegistry.register(SceneManager())
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
                            },
                        ),
                )
                EmptyNode(name = "external")
            }

        ManagersRegistry.get(SceneManager::class).currScene = tree

        assertEquals("external", referencedNode)
    }
}
