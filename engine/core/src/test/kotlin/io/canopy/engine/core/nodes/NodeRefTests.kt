package io.canopy.engine.core.nodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.core.nodes.core.NodeRef
import io.canopy.engine.core.nodes.core.asSceneRoot
import io.canopy.engine.core.nodes.core.behavior
import io.canopy.engine.core.nodes.core.nodeRef
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.BeforeAll

class NodeRefTests {

    /**
     * Node that stores an external node reference.
     *
     * In real usage this pattern is common for "wiring" nodes together without
     * requiring the target to be constructed first.
     */
    private class NeedsRef(name: String, val external: NodeRef<*>, block: NeedsRef.() -> Unit = {}) :
        Node<NeedsRef>(name, block)

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            // Tests run in a shared JVM; reset manager state to avoid cross-test contamination.
            ManagersRegistry.withScope {
                register(SceneManager())
            }
        }
    }

    @Test
    fun `path nodeRef should resolve from scene root`() {
        var referencedNodeName: String? = null

        // Build a tree where "referrer" holds a reference to "$/external".
        val tree =
            EmptyNode(name = "root") {
                NeedsRef(
                    name = "referrer",
                    external = nodeRef("$/external") // `$` means "resolve from current scene root"
                ) {
                    behavior(
                        onReady = {
                            // Resolve the reference relative to this node.
                            referencedNodeName = external.get(this).name
                        }
                    )
                }

                EmptyNode(name = "external")
            }.asSceneRoot()

        // Triggers enterTree + ready; behaviors run in ready.
        tree.buildTree()

        assertNotNull(referencedNodeName, "Expected reference to be resolved during onReady()")
        assertEquals("external", referencedNodeName)
    }
}
