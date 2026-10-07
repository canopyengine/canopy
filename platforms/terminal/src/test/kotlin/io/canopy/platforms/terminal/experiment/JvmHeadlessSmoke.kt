package io.canopy.platforms.terminal.experiment

import kotlin.time.Duration.Companion.seconds
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.canopy.engine.core.managers.manager
import io.canopy.engine.data.assets.AssetsManager
import io.canopy.engine.data.assets.FileSource
import io.canopy.engine.data.assets.WritableAssetEntry
import kotlinx.coroutines.runBlocking

/** Real JVM experiment, packaged without terminal input, graphics, native libraries or JUnit. */
fun main() {
    listOf(
        "com.badlogic.gdx.Gdx",
        "ktx.app.KtxGame",
        "com.github.ajalt.mordant.terminal.Terminal",
        "org.junit.jupiter.api.Test"
    ).forEach { name ->
        check(runCatching { Class.forName(name) }.exceptionOrNull() is ClassNotFoundException) {
            "Unexpected experiment dependency: $name"
        }
    }
    val forbidden = listOf("gdx", "libktx", "ktx-", "mordant", "jna", "natives", "lwjgl")
    val paths = System.getProperty("java.class.path").split(System.getProperty("path.separator"))
    check(paths.none { path -> forbidden.any { Path.of(path).fileName.toString().lowercase().contains(it) } })

    val caller = Thread.currentThread()
    val lifecycle = mutableListOf<String>()
    var frames = 0
    val app = JvmHeadlessApp().apply {
        onEnter {
            check(Thread.currentThread() === caller)
            lifecycle += "enter"
            verifyAssets(manager<AssetsManager>())
        }
        onUpdate {
            check(Thread.currentThread() === caller)
            if (++frames == 3) handle.requestExit()
        }
        onExit {
            check(Thread.currentThread() === caller)
            lifecycle += "exit"
        }
    }
    app.launch()
    check(frames == 3 && lifecycle == listOf("enter", "exit"))

    // Deterministic external request while the host is blocked inside its sleeper.
    val sleeping = CountDownLatch(1)
    val release = CountDownLatch(1)
    val stopped = CountDownLatch(1)
    var interruptedOnExit = false
    val wakeApp = JvmHeadlessApp(
        JvmHeadlessHost(sleep = {
            sleeping.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "External stop did not wake sleeper" }
        })
    ).apply {
        onExit {
            interruptedOnExit = Thread.currentThread().isInterrupted
            stopped.countDown()
        }
    }
    val handle = wakeApp.launchAsync("jvm-headless-smoke")
    check(sleeping.await(5, TimeUnit.SECONDS)) { "Host did not reach sleeper" }
    handle.requestExit()
    check(stopped.await(5, TimeUnit.SECONDS)) { "Host did not stop" }
    runBlocking { check(handle.join(5.seconds)) { "Host teardown did not complete" } }
    check(interruptedOnExit)

    // This is a probe report, not an engine diagnostic or a performance measurement.
    System.out.printf(
        "JVM headless smoke passed: %d frames; filesystem, JAR assets and external interrupt verified.%n",
        frames
    )
}

private fun verifyAssets(assets: AssetsManager) {
    val root = Files.createTempDirectory(Path.of("."), "jvm-headless-assets-")
    val homeBefore = System.getProperty("user.home")
    try {
        val path = root.resolve("world.txt")
        listOf(FileSource.Internal, FileSource.External, FileSource.Absolute).forEach { source ->
            val entry = assets.loadFile(path.toString(), source) as WritableAssetEntry
            entry.writeText("world")
            entry.writeText("+commands", append = true)
            check(entry.readText() == "world+commands")
        }
        check(assets.loadFile(root.toString()).list().any { it.name == "world.txt" })
        System.setProperty("user.home", root.toAbsolutePath().toString())
        check(assets.loadFile("world.txt", FileSource.Local).readText() == "world+commands")

        val resource = "jvm-headless-world.txt"
        check(Thread.currentThread().contextClassLoader.getResource(resource)?.protocol == "jar")
        val classpath = assets.loadFile(resource, FileSource.Classpath)
        check(classpath.readText().trim() == "ecosystem seed")
        check(runCatching { (classpath as WritableAssetEntry).writeText("forbidden") }.isFailure)
        val missing = assets.loadFile(path.toString(), FileSource.Classpath)
        check(!missing.exists())
        check(runCatching { missing.readText() }.exceptionOrNull() is java.io.FileNotFoundException)
    } finally {
        System.setProperty("user.home", homeBefore)
        Files.walk(root).use { entries -> entries.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
