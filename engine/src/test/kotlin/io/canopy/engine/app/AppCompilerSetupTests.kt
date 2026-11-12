package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.lang.reflect.InvocationTargetException
import io.canopy.engine.core.exceptions.CompilerSetupException
import kotlinx.coroutines.runBlocking

class AppCompilerSetupTests {
    private class TestApp : App<AppConfig>() {
        var hostStarts = 0
        var received: List<String> = emptyList()
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) {
            hostStarts++
            received = args.toList()
            engineLoop.enter()
            engineLoop.exit()
        }
    }

    @Test
    fun `unprocessed startup reports setup before reserving the app even after another compiled launch`() {
        // Arrange
        TestApp().launch()
        listOf("launch", "launchAsync").forEach { name ->
            val app = TestApp()
            val types = if (name == "launch") {
                arrayOf(Array<String>::class.java)
            } else {
                arrayOf(String::class.java, Array<String>::class.java)
            }
            val arguments: Array<Any> = if (name == "launch") {
                arrayOf(emptyArray<String>())
            } else {
                arrayOf("unprocessed-app", emptyArray<String>())
            }

            // Act: reflection reaches the same public entry as an unprocessed consumer call.
            val failure = assertFailsWith<InvocationTargetException> {
                App::class.java.getMethod(name, *types).invoke(app, *arguments)
            }

            // Assert
            val setup = assertIs<CompilerSetupException>(failure.cause)
            assertTrue(setup.message!!.contains("CANOPY_COMPILER_REQUIRED"))
            assertTrue(setup.message!!.contains("io.github.canopyengine.compiler"))
            assertEquals(0, app.hostStarts)
            app.launch("verified")
            assertEquals(1, app.hostStarts)
            assertEquals(listOf("verified"), app.received)
        }
    }

    @Test
    fun `compiled bound and unbound startup references preserve arguments and public names`() {
        // Arrange
        val boundApp = TestApp()
        val unboundApp = TestApp()
        val bound = boundApp::launch
        val unbound = App<AppConfig>::launch

        // Act
        bound(arrayOf("bound"))
        unbound(unboundApp, arrayOf("unbound"))

        // Assert
        assertEquals("launch", bound.name)
        assertEquals("launch", unbound.name)
        assertEquals(listOf("bound"), boundApp.received)
        assertEquals(listOf("unbound"), unboundApp.received)
    }

    @Test
    fun `compiled asynchronous reference completes the same app handle`() = runBlocking {
        // Arrange
        val app = TestApp()
        val start = app::launchAsync

        // Act
        val handle = start("compiled-app", arrayOf("async"))
        handle.join()

        // Assert
        assertEquals(app.handle, handle)
        assertEquals(listOf("async"), app.received)
    }
}
