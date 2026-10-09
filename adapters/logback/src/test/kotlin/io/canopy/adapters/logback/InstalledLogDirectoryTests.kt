package io.canopy.adapters.logback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Path

class InstalledLogDirectoryTests {
    private val home = Path.of(System.getProperty("java.io.tmpdir")).resolve("test-home").toAbsolutePath()

    @Test
    fun `installed location is explicit and resolves each desktop platform`() {
        assertEquals(
            home.resolve("local/Canopy/Ecosystem/logs"),
            installedLogDirectory("Canopy", "Ecosystem", "Windows 11", { home.resolve("local").toString() }, home)
        )
        assertEquals(
            home.resolve("AppData/Local/Canopy/Ecosystem/logs"),
            installedLogDirectory("Canopy", "Ecosystem", "Windows 11", { null }, home)
        )
        assertEquals(
            home.resolve("state/Canopy/Ecosystem/logs"),
            installedLogDirectory("Canopy", "Ecosystem", "Linux", { home.resolve("state").toString() }, home)
        )
        assertEquals(
            home.resolve(".local/state/Canopy/Ecosystem/logs"),
            installedLogDirectory("Canopy", "Ecosystem", "Linux", { "relative/path" }, home)
        )
        assertEquals(
            home.resolve("Library/Logs/Canopy/Ecosystem"),
            installedLogDirectory(
                "Canopy",
                "Ecosystem",
                "Mac OS X",
                { error("macOS does not use these environment paths") },
                home
            )
        )
        assertEquals(Path.of(".canopy", "logs"), LogbackLogging.Config().baseLogDir)
    }

    @Test
    fun `installed directory names reject traversal separators and non portable names`() {
        val names =
            listOf("", "..", "../escape", "parent/child", "parent\\child", "C:escape", "CON", "trailing.", "trailing ")
        for (name in names) {
            assertFailsWith<IllegalArgumentException> { installedLogDirectory(name, "Game", "Linux", { null }, home) }
            assertFailsWith<IllegalArgumentException> {
                installedLogDirectory("Publisher", name, "Linux", { null }, home)
            }
        }
        assertFailsWith<IllegalArgumentException> { LogbackLogging.Retention(minimumRuns = -1) }
        assertFailsWith<IllegalArgumentException> { LogbackLogging.Retention(targetBytes = -1) }
    }
}
