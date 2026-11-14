package io.canopy.tooling.compiler.gradle

import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/**
 * Runs an installed application through Canopy's bundled launcher, opening terminal games in their own window.
 * Uses the application/run task's configured Java toolchain, JVM options, environment and working directory.
 * Inherits JavaExec's --args parsing. Configure application/run as usual; no source launcher file is needed.
 * This task does not intercept an IDE's native Main runner or provide an IDE debugger integration.
 */
@DisableCachingByDefault(because = "Launches an interactive application")
abstract class CanopyRun @Inject constructor(private val processes: ExecOperations) : JavaExec() {
    /** Installed distribution's lib directory, supplied by installDist. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraries: DirectoryProperty

    /** Private generated launcher and manifest directory under this project's build directory. */
    @get:Internal
    abstract val launcherDirectory: DirectoryProperty

    /** Uses the caller's console instead of opening a terminal window; useful for automation. */
    @get:Input
    @get:Option(option = "current-terminal", description = "Use the caller's console instead of a separate window")
    abstract val currentTerminal: Property<Boolean>

    @TaskAction
    override fun exec() {
        val directory = launcherDirectory.get().asFile.apply { mkdirs() }
        val launcher = directory.resolve("CanopyLaunch.java")
        CanopyRun::class.java.getResourceAsStream("launcher/CanopyLaunch.java")!!.use { source ->
            launcher.outputStream().use(source::copyTo)
        }
        val java = javaLauncher.get().executablePath.asFile.absolutePath
        val manifest = directory.resolve("launch.bin")
        try {
            val fields = listOf(
                workingDir.absolutePath,
                java,
                libraries.get().asFile.absolutePath,
                mainClass.get(),
                mainModule.orNull.orEmpty()
            ) + allJvmArgs
            manifest.writeText(fields.joinToString("\u0000"), Charsets.UTF_8)
            val command = buildList {
                addAll(listOf(java, launcher.absolutePath, "--manifest", manifest.absolutePath))
                if (currentTerminal.get()) add("--current-terminal")
                add("--")
                addAll(args.orEmpty())
            }
            processes.exec {
                it.commandLine(command)
                it.workingDir(workingDir)
                it.setEnvironment(environment)
                standardInput?.let { stream -> it.standardInput = stream }
                standardOutput?.let { stream -> it.standardOutput = stream }
                errorOutput?.let { stream -> it.errorOutput = stream }
                it.isIgnoreExitValue = isIgnoreExitValue
            }
        } finally {
            // JVM options may contain private values; do not retain them after the process finishes.
            manifest.delete()
        }
    }
}
