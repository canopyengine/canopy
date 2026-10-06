package io.canopy.platforms.terminal.data.assets

import kotlin.test.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import io.canopy.engine.data.assets.FileSource
import org.junit.jupiter.api.io.TempDir

class TerminalAssetEntryTests {
    @TempDir
    lateinit var directory: Path

    private fun resource(stream: InputStream): TerminalAssetEntry {
        val url = URL(
            null,
            "test:resource",
            object : URLStreamHandler() {
                override fun openConnection(url: URL) = object : URLConnection(url) {
                    override fun connect() = Unit
                    override fun getInputStream() = stream
                }
            }
        )
        return TerminalAssetEntry(File("resource"), FileSource.Classpath, url)
    }

    @Test
    fun `classpath stream closes after successful read`() {
        var closed = false
        val stream = object : InputStream() {
            private val bytes = "asset".byteInputStream()
            override fun read() = bytes.read()
            override fun close() {
                closed = true
            }
        }
        val entry = resource(stream)
        assertEquals("asset", entry.readText())
        assertTrue(closed)
        assertFailsWith<IllegalArgumentException> { entry.writeText("changed") }
    }

    @Test
    fun `classpath stream closes when reading fails`() {
        var closed = false
        val failure = IOException("read")
        val entry = resource(object : InputStream() {
            override fun read(): Int = throw failure
            override fun close() {
                closed = true
            }
        })
        assertSame(failure, assertFailsWith<IOException> { entry.readBytes() })
        assertTrue(closed)
    }

    @Test
    fun `missing classpath resource never reads or writes an existing filesystem file`() {
        val file = directory.resolve("unrelated.txt").toFile().apply { writeText("original") }
        val entry = TerminalAssetEntry.create(file.path, FileSource.Classpath)
        assertFalse(entry.exists())
        assertFailsWith<java.io.FileNotFoundException> { entry.readText() }
        assertFailsWith<IllegalArgumentException> { entry.writeText("overwrite") }
        assertEquals("original", file.readText())
    }

    @Test
    fun `missing classpath directory has no filesystem children`() {
        directory.resolve("child").toFile().writeText("data")
        val entry = TerminalAssetEntry.create(directory.toString(), FileSource.Classpath)
        assertFalse(entry.exists())
        assertFalse(entry.isDirectory)
        assertTrue(entry.list().isEmpty())
    }

    @Test
    fun `file and jar classpath resources resolve without allowing overwrite or append`() {
        directory.resolve("resource.txt").toFile().writeText("file resource")
        val jar = directory.resolve("resources.jar").toFile()
        JarOutputStream(jar.outputStream()).use {
            it.putNextEntry(JarEntry("resource.txt"))
            it.write("jar resource".encodeToByteArray())
            it.closeEntry()
        }
        listOf(
            directory.toUri().toURL() to "file resource",
            jar.toURI().toURL() to "jar resource"
        ).forEach { (url, expected) ->
            URLClassLoader(arrayOf(url), null).use { loader ->
                val thread = Thread.currentThread()
                val previous = thread.contextClassLoader
                try {
                    thread.contextClassLoader = loader
                    val entry = TerminalAssetEntry.create("resource.txt", FileSource.Classpath)
                    assertTrue(entry.exists())
                    assertEquals(expected, entry.readText())
                    assertFailsWith<IllegalArgumentException> { entry.writeText("overwrite") }
                    assertFailsWith<IllegalArgumentException> { entry.writeText("append", append = true) }
                } finally {
                    thread.contextClassLoader = previous
                }
            }
        }
        assertEquals("file resource", directory.resolve("resource.txt").toFile().readText())
    }

    @Test
    fun `filesystem source still supports writing and reading`() {
        val file = directory.resolve("asset.txt").toFile()
        val entry = TerminalAssetEntry.create(file.path, FileSource.Absolute)
        assertFalse(entry.exists())
        entry.writeText("first")
        entry.writeText("second", append = true)
        assertTrue(entry.exists())
        assertEquals("firstsecond", entry.readText())
    }
}
