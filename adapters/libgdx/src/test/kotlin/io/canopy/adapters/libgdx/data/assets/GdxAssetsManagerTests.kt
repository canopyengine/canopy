package io.canopy.adapters.libgdx.data.assets

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.data.assets.FileSource

class GdxAssetsManagerTests {

    private var app: HeadlessApplication? = null

    @AfterTest
    fun cleanup() {
        app?.exit()
        app = null
    }

    private fun startHeadless() {
        if (app == null) {
            app = HeadlessApplication(object : ApplicationAdapter() {}, HeadlessApplicationConfiguration())
        }
    }

    @Test
    fun `resolveFile supports absolute files`() {
        startHeadless()
        val tempFile = kotlin.io.path.createTempFile("canopy-gdx-absolute", ".txt").toFile()
        tempFile.writeText("value")

        val manager = GdxAssetsManager()
        val file = manager.resolveFile(tempFile.absolutePath, FileSource.Absolute)

        assertTrue(file.exists())
        assertEquals(tempFile.absolutePath.replace('\\', '/'), file.path().replace('\\', '/'))
    }

    @Test
    fun `loadFile returns asset entry and applies custom options`() {
        startHeadless()
        val tempFile = kotlin.io.path.createTempFile("canopy-gdx-load", ".txt").toFile()
        tempFile.writeText("value")

        var configuredPath = ""
        val asset = GdxAssetsManager().loadFile(tempFile.absolutePath, FileSource.Absolute) {
            configuredPath = path
        }

        assertIs<GdxAssetEntry>(asset)
        assertEquals("value", asset.readText())
        assertEquals(asset.path, configuredPath)
    }
}
