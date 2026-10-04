import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

val canopyVersion = project.property("canopyVersion") ?: ""

plugins {
    base
    id("io.canopy.node-state") apply false
    jacoco
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}

group = "io.canopy"
version = canopyVersion

allprojects {

    apply(plugin = "eclipse")
    apply(plugin = "idea")

    group = "io.canopy"
    version = canopyVersion

    extensions.configure<IdeaModel> {
        module {
            outputDir = file("build/classes/java/main")
            testOutputDir = file("build/classes/java/test")
        }
    }
}

subprojects {
    plugins.withType<BasePlugin> {
        extensions.configure<BasePluginExtension>("base") {
            archivesName.set(project.path.removePrefix(":").replace(":", "-"))
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        pluginManager.apply("io.canopy.node-state")
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            sourceCompatibility = JavaVersion.VERSION_25
            targetCompatibility = JavaVersion.VERSION_25
            withSourcesJar()
        }
    }

    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            sourceCompatibility = JavaVersion.VERSION_25
            targetCompatibility = JavaVersion.VERSION_25
            withSourcesJar()
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            publications {
                create("mavenJava", MavenPublication::class.java) {
                    val javaComponent = components.findByName("java")
                    if (javaComponent != null) {
                        from(javaComponent)
                    }

                    artifactId = project.path.removePrefix(":").replace(":", "-")

                    pom {
                        name.set(project.name)
                        description.set("Canopy module: ${project.path}")
                    }
                }
            }

            repositories {
                mavenLocal()
            }
        }
    }

    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

extensions.configure<EclipseModel> {
    project.name = "canopy-parent"
}

tasks.named("clean", Delete::class.java) {
    delete(
        rootDir.walkTopDown()
            .filter { it.isDirectory && it.name == ".canopy" }
            .toList()
    )
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Runs all JVM tests and generates an aggregated JaCoCo coverage report."

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }

    reports.xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/coverageReport/coverageReport.xml"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/coverageReport/html"))

    doLast {
        val xmlReport = reports.xml.outputLocation.get().asFile
        val lineCounter = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"\s*/>""")
            .findAll(xmlReport.readText())
            .lastOrNull()
            ?: error("Could not find aggregate LINE coverage in ${xmlReport.absolutePath}")
        val missed = lineCounter.groupValues[1].toLong()
        val covered = lineCounter.groupValues[2].toLong()
        val total = missed + covered
        val percentage = if (total == 0L) 0.0 else covered * 100.0 / total
        println("Line coverage: $covered / $total lines (${"%.1f".format(percentage)}%)")
        check(percentage >= 60.0) {
            "Aggregate line coverage ${"%.1f".format(percentage)}% is below the 60% target."
        }
        println("JaCoCo XML: ${xmlReport.absolutePath}")
        println("JaCoCo HTML: ${reports.html.outputLocation.get().asFile.resolve("index.html").absolutePath}")
    }
}

subprojects {
    plugins.withId("java") {
        pluginManager.apply("jacoco")

        val sourceSets = extensions.getByType<SourceSetContainer>()
        val mainSourceSet = sourceSets.named("main")
        val testTasks = tasks.withType<Test>()

        coverageReport.configure {
            dependsOn(testTasks)
            executionData(testTasks)
            sourceDirectories.from(mainSourceSet.map { it.allSource.srcDirs })
            classDirectories.from(mainSourceSet.map { it.output })
        }
    }
}

// Compiler tooling is an included build so its consumer plugin is available during project configuration.
listOf("test", "ktlintCheck", "build").forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        dependsOn(gradle.includedBuild("node-gradle-plugin").task(":$taskName"))
        dependsOn(gradle.includedBuild("node-gradle-plugin").task(":engine-compiler:$taskName"))
    }
}

// A source-built engine and its consumer compiler integration must use the same version.
val publishLocalTooling = tasks.register("publishToMavenLocal") {
    dependsOn(gradle.includedBuild("node-gradle-plugin").task(":publishToMavenLocal"))
    dependsOn(gradle.includedBuild("node-gradle-plugin").task(":engine-compiler:publishToMavenLocal"))
}
subprojects {
    plugins.withId("maven-publish") {
        val modulePublication = tasks.named("publishToMavenLocal")
        publishLocalTooling.configure { dependsOn(modulePublication) }
    }
}

val allTests = tasks.register("test") { group = "verification" }
val allLint = tasks.register("ktlintCheck") { group = "verification" }
subprojects {
    plugins.withId("java") {
        val moduleTests = tasks.named("test")
        allTests.configure { dependsOn(moduleTests) }
    }
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        val moduleLint = tasks.named("ktlintCheck")
        allLint.configure { dependsOn(moduleLint) }
    }
}

coverageReport.configure {
    mapOf(":test" to "tooling/node-gradle-plugin", ":engine-compiler:test" to "engine/compiler")
        .forEach { (taskPath, moduleDirectory) ->
            dependsOn(gradle.includedBuild("node-gradle-plugin").task(taskPath))
            executionData(file("$moduleDirectory/build/jacoco/test.exec"))
            sourceDirectories.from(file("$moduleDirectory/src/main/kotlin"))
            classDirectories.from(file("$moduleDirectory/build/classes/kotlin/main"))
        }
}
