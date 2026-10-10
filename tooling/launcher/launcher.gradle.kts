import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.jvm.application.tasks.CreateStartScripts

// Build the standard distribution; its start script runs outside Gradle.
allprojects {
    pluginManager.withPlugin("application") {
        val app = extensions.getByType<JavaApplication>()
        val install = tasks.named<Sync>("installDist")
        val scripts = tasks.named<CreateStartScripts>("startScripts")
        tasks.register("canopyLaunchManifest") {
            dependsOn(install)
            doLast {
                val run = tasks.named<JavaExec>("run").get()
                val scriptName =
                    scripts.get().applicationName + if (System.getProperty("os.name").startsWith("Windows")) {
                        ".bat"
                    } else {
                        ""
                    }
                val script = install.get().destinationDir.resolve(app.executableDir).resolve(scriptName)
                check(script.isFile) { "Installed launcher not found: $script" }
                val fields = listOf(
                    projectDir.absolutePath,
                    run.javaLauncher.get().metadata.installationPath.asFile.absolutePath,
                    script.absolutePath
                )
                check(fields.none { '\n' in it || '\r' in it }) { "Launcher paths cannot contain line breaks" }
                file(providers.gradleProperty("canopyLaunchManifest").get())
                    .writeText(fields.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }
        }
    }
}
