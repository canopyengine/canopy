package io.canopy.tooling.compiler.gradle

import io.canopy.tooling.diagnostics.Diagnostic
import io.canopy.tooling.diagnostics.DiagnosticColor
import io.canopy.tooling.diagnostics.DiagnosticFormat
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Installs the general Canopy compiler plugin in every Kotlin compilation, including tests.
 * Rejects incompatible Kotlin versions because rule providers use the compiler IR API.
 */
class CanopyCompilerPlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("application") {
            val run = target.tasks.named("run", JavaExec::class.java)
            val install = target.tasks.named("installDist", Sync::class.java)
            target.tasks.register("canopyRun", CanopyRun::class.java) { task ->
                task.group = "application"
                task.description = "Run the game directly, opening terminal games in a separate window."
                task.dependsOn(install)
                task.currentTerminal.convention(false)
                task.libraries.set(target.layout.dir(install.map { it.destinationDir.resolve("lib") }))
                task.launcherDirectory.set(target.layout.buildDirectory.dir("canopy/launcher"))
                task.mainClass.convention(run.flatMap { it.mainClass })
                task.mainModule.convention(run.flatMap { it.mainModule })
                task.javaLauncher.convention(run.flatMap { it.javaLauncher })
                task.classpath = run.get().classpath
                task.setJvmArgs(run.get().allJvmArgs)
                task.workingDir(run.get().workingDir)
                task.setEnvironment(run.get().environment)
                task.setArgs(run.get().args)
                task.standardInput = System.`in`
            }
        }
        val rules = target.configurations.create("canopyCompilerRules") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
            it.description = "Additional Canopy compiler rule providers (never part of the game runtime)."
        }
        // Select the compiler host variant only for our artifact, preserving other compiler plugins' requirements.
        target.configurations.configureEach { configuration ->
            if (configuration.name.startsWith("kotlinCompilerPluginClasspath")) {
                configuration.extendsFrom(rules)
                configuration.dependencies.withType(ModuleDependency::class.java).configureEach { dependency ->
                    if (dependency.group == "io.github.canopyengine" && dependency.name == "canopy-compiler") {
                        dependency.capabilities { handler ->
                            handler.requireCapability("io.github.canopyengine:canopy-compiler-checks")
                        }
                    }
                }
            }
        }
    }
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
    override fun getCompilerPluginId(): String = "io.github.canopyengine.compiler"
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact("io.github.canopyengine", "canopy-compiler", CANOPY_VERSION)
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val version = project.getKotlinPluginVersion()
        if (version != CANOPY_KOTLIN_VERSION) {
            throw GradleException(
                DiagnosticFormat.render(
                    Diagnostic(
                        "CANOPY_KOTLIN_VERSION",
                        "This Kotlin version is not supported",
                        label = "Canopy $CANOPY_VERSION requires Kotlin $CANOPY_KOTLIN_VERSION; " +
                            "${project.path} uses $version.",
                        help = "Use Kotlin $CANOPY_KOTLIN_VERSION or a matching Canopy release."
                    )
                )
            )
        }
        return project.provider {
            val mode = project.providers.gradleProperty("canopy.diagnostics.color").orElse("auto").get().lowercase()
            if (mode !in setOf("auto", "always", "never")) {
                throw GradleException(
                    DiagnosticFormat.render(
                        Diagnostic(
                            "CANOPY_DIAGNOSTIC_COLOR",
                            "Unknown diagnostic color mode",
                            label = "Requested: $mode",
                            help = "Set canopy.diagnostics.color to auto, always or never."
                        )
                    )
                )
            }
            val console = project.gradle.startParameter.consoleOutput
            val ide = project.providers.systemProperty("idea.active").orNull == "true" ||
                project.providers.systemProperty("idea.version").isPresent ||
                project.providers.gradleProperty("android.injected.invoked.from.ide").orNull == "true"
            val ci = project.providers.environmentVariable("CI").orNull?.lowercase().let {
                it != null && it != "false" && it != "0"
            }
            val colors = DiagnosticColor.enabled(
                mode,
                rich = console == ConsoleOutput.Rich || console == ConsoleOutput.Verbose,
                plain = console == ConsoleOutput.Plain,
                noColor = project.providers.environmentVariable("NO_COLOR").isPresent,
                ci = ci,
                ide = ide
            )
            listOf(
                SubpluginOption("diagnosticColor", colors.toString()),
                SubpluginOption("diagnosticRoot", project.projectDir.absolutePath)
            )
        }
    }
}
