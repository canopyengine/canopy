package io.canopy.devtools.data.assets

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InMemoryAssetEntryTests {

    @Test
    fun `reads writes and appends text and bytes`() {
        val entry = InMemoryAssetEntry("dir/file.txt", "hi")

        entry.writeText("-there", append = true)
        assertEquals("hi-there", entry.readText())

        entry.writeBytes(byteArrayOf(1, 2), append = false)
        assertContentEquals(byteArrayOf(1, 2), entry.readBytes())

        entry.writeBytes(byteArrayOf(3), append = true)
        assertContentEquals(byteArrayOf(1, 2, 3), entry.readBytes())
    }

    @Test
    fun `directory children can be listed and cleared`() {
        val root = InMemoryAssetEntry("root", isDirectory = true)
        root.addChild(InMemoryAssetEntry("root/a.txt"))
        root.addChild(InMemoryAssetEntry("root/b.txt"))

        assertEquals(listOf("a.txt", "b.txt"), root.list().map { it.name })

        root.clearChildren()

        assertTrue(root.list().isEmpty())
    }

    @Test
    fun `non directory cannot accept children`() {
        val file = InMemoryAssetEntry("file.txt")

        assertFailsWith<IllegalArgumentException> {
            file.addChild(InMemoryAssetEntry("other.txt"))
        }
    }
}
