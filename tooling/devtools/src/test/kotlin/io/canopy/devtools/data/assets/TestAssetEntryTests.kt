package io.canopy.devtools.data.assets

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class TestAssetEntryTests {

    private val tempDirs = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `entry creates parent directories and reads writes content`() {
        val root = createTempDirectory("canopy-test-asset")
        tempDirs.add(root)
        val file = root.resolve("nested/data.bin").toFile()
        val entry = TestAssetEntry(file)

        entry.writeText("hello", append = false)
        entry.writeBytes(byteArrayOf(1, 2), append = true)

        assertTrue(entry.exists())
        assertEquals("data.bin", entry.name)
        assertEquals("bin", entry.extension)
        assertContentEquals("hello".encodeToByteArray() + byteArrayOf(1, 2), entry.readBytes())
    }

    @Test
    fun `directory lists nested files`() {
        val root = createTempDirectory("canopy-test-asset-dir")
        tempDirs.add(root)
        root.resolve("a.txt").toFile().writeText("a")
        root.resolve("b.txt").toFile().writeText("b")

        val entry = TestAssetEntry(root.toFile())

        assertEquals(listOf("a.txt", "b.txt"), entry.list().map { it.name }.sorted())
    }
}
