package io.canopy.engine.core.flows

import kotlin.test.*
import io.canopy.engine.core.flows.events.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import org.junit.jupiter.api.AfterEach

class ObservableCollectionTests {
    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `list copies inputs and observes iterator sublist bulk and replacement mutations`() {
        // Arrange
        val input = mutableListOf(1, 2)
        var numbers by signal(input)
        val totals = mutableListOf<Int>()
        val reaction = effect { totals += numbers.sum() }
        input.add(100)
        assertEquals(listOf(3), totals)

        // Act
        numbers.addAll(listOf(3, 4))
        numbers.listIterator().apply {
            next()
            set(5)
        }
        numbers.subList(1, 3).clear()
        numbers.iterator().apply {
            next()
            remove()
        }
        val previous = numbers
        numbers = mutableListOf(8)
        previous.add(99)
        numbers[0] = 8
        numbers.clear()

        // Assert
        assertEquals(listOf(3, 10, 14, 9, 4, 8, 0), totals)
        reaction.dispose()
    }

    @Test
    fun `captured collection views remain observable until their source is replaced`() {
        // Arrange
        val source = signal(mutableListOf(1))
        val captured = source()
        val total = computed { captured.sum() }
        assertEquals(1, total())

        // Act / Assert
        captured.add(2)
        assertEquals(3, total())
        source.update { mutableListOf(7) }
        captured.add(4)
        assertEquals(3, total())
        assertEquals(listOf(7), source())
        total.dispose()
        source.dispose()
    }

    @Test
    fun `set mutations suppress duplicates and notify iterator removal`() {
        // Arrange
        val input = mutableSetOf("a")
        val source = signal(input)
        val sizes = mutableListOf<Int>()
        val reaction = effect { sizes += source().size }

        // Act
        input.add("outside")
        source().add("a")
        source().remove("missing")
        source().add("b")
        source().iterator().apply {
            next()
            remove()
        }
        source().clear()
        source().clear()

        // Assert
        assertEquals(listOf(1, 2, 1, 0), sizes)
        reaction.dispose()
        source.dispose()
    }

    @Test
    fun `map entry keys values and iterator mutations notify and preserve null values`() {
        // Arrange
        val input = mutableMapOf("a" to 1, "b" to 2)
        val source = signal(input)
        val values = mutableListOf<Int>()
        val reaction = effect { values += source().values.sum() }

        // Act
        input["outside"] = 100
        source().entries.first { it.key == "a" }.setValue(3)
        source().keys.remove("b")
        source()["c"] = 4
        source().values.remove(3)
        source().entries.iterator().apply {
            next()
            remove()
        }
        val nullable = signal(mutableMapOf<String, Int?>("null" to null))
        val sizes = mutableListOf<Int>()
        val nullReaction = effect { sizes += nullable().size }
        nullable()["other"] = null
        nullable()["other"] = null
        nullable().keys.remove("null")

        // Assert
        assertEquals(listOf(3, 5, 3, 7, 4, 0), values)
        assertEquals(listOf(1, 2, 1), sizes)
        reaction.dispose()
        nullReaction.dispose()
        source.dispose()
        nullable.dispose()
    }

    @Test
    fun `reentrant collection edits settle without dropping the final calculation`() {
        // Arrange
        val source = signal(mutableListOf<Int>())
        val size = computed { source().size }
        val seen = mutableListOf<Int>()
        val reaction = effect {
            val count = size()
            seen += count
            if (count == 1) source().add(2)
        }

        // Act
        source().add(1)

        // Assert
        assertEquals(listOf(0, 1, 2), seen)
        assertEquals(2, size())
        reaction.dispose()
        size.dispose()
        source.dispose()
    }

    private class Item : Node<Item>("item") {
        var hunger by signal(1)
    }
    private class Inventory : Node<Inventory>("inventory") {
        var items by signal(mutableListOf<Item>())
        val total by computed { items.sumOf { it.hunger } }
    }

    @Test
    fun `node collections track element state and suspend while detached without observing plain fields`() {
        // Arrange
        lateinit var scenes: SceneManager
        ManagersRegistry.withScope {
            scenes = SceneManager()
            register(scenes)
        }
        val root = EmptyNode("root")
        val inventory = Inventory()
        val item = Item()
        root.addChild(inventory)
        root.addChild(item)
        scenes.currScene = root
        inventory.items.add(item)
        assertEquals(1, inventory.total)

        // Act / Assert
        item.hunger = 5
        assertEquals(5, inventory.total)
        root.removeChild(inventory)
        inventory.items.clear()
        assertEquals(0, inventory.total)
        inventory.items.add(item)
        root.addChild(inventory)
        assertEquals(5, inventory.total)
        item.hunger = 7
        assertEquals(7, inventory.total)
        inventory.queueFree()
        scenes.onUpdate(0f)
        assertFails { inventory.items }
    }
}
