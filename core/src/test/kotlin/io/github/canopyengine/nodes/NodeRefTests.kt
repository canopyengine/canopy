package anchors.framework.utils.nodes

import anchors.framework.managers.ManagersRegistry
import anchors.framework.nodes.SceneManager
import anchors.framework.nodes.core.Behavior
import anchors.framework.nodes.core.Node
import anchors.framework.nodes.core.NodeRef
import anchors.framework.nodes.core.behavior
import anchors.framework.nodes.core.nodeRef
import anchors.framework.nodes.types.empty.EmptyNode
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
