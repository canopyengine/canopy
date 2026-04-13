package io.canopy.adapters.libgdx.data.assets

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.badlogic.gdx.files.FileHandle

@OptIn(ExperimentalPathApi::class)
class GdxAssetEntryTests {

    private val tempRoots = mutableListOf<java.nio.file.Path>()

    @AfterTest
    fun cleanup() {
        tempRoots.forEach { it.deleteRecursively() }
        tempRoots.clear()
    }

    @Test
    fun `entry delegates file properties and read operations`() {
        val root = createTempDirectory("canopy-gdx-asset")
        tempRoots.add(root)
        val file = root.resolve("hello.txt")
        file.writeText("hello")

        val entry = GdxAssetEntry(FileHandle(file.toFile()))

        assertTrue(entry.exists())
        assertEquals("hello.txt", entry.name)
        assertEquals("txt", entry.extension)
        assertFalse(entry.isDirectory)
        assertEquals("hello", entry.readText())
        assertContentEquals("hello".encodeToByteArray(), entry.readBytes())
        assertEquals(file.toFile().path.replace('\\', '/'), entry.path.replace('\\', '/'))
    }

    @Test
    fun `entry writes and lists children`() {
        val root = createTempDirectory("canopy-gdx-dir")
        tempRoots.add(root)
        val child = root.resolve("child.txt")
        child.writeText("first")

        val directory = GdxAssetEntry(FileHandle(root.toFile()))
        val fileEntry = directory.list().single() as GdxAssetEntry
        fileEntry.writeText("-second", append = true)

        assertTrue(directory.isDirectory)
        assertEquals(listOf("child.txt"), directory.list().map { it.name })
        assertEquals("first-second", child.toFile().readText())
    }
}
