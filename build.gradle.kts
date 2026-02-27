import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

val canopyVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.axion.release) apply true
}

scmVersion {
    tag {
        prefix.set("v") // tags like v0.1.0
    }
    // Optional: if no tag yet, start from something:
    versionCreator("simple")
}

allprojects {
    apply(plugin = "eclipse")
    apply(plugin = "idea")

    group = "io.github.canopyengine"
    version = rootProject.scmVersion.version

    extensions.configure<IdeaModel> {
        module {
            outputDir = file("build/classes/kotlin/main")
            testOutputDir = file("build/classes/kotlin/test")
        }
    }
}



subprojects {
    // Grouping projects should not behave like real modules
    val ignoredPaths = listOf(
        ":engine",
        ":engine:app",
        ":engine:data"
    )

    if (path in ignoredPaths) return@subprojects

    // Apply plugins HERE so Kotlin DSL dependency accessors exist in this script block
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
        // withJavadocJar()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    dependencies {
        val libs = rootProject.libs

        // Kotlin
        "api"(libs.kotlin.stdlib)
        "implementation"(libs.kotlin.reflect)
        "api"(libs.coroutines.core)

        // Testing
        "testImplementation"(libs.kotlin.test.junit5)
        "testImplementation"(libs.junit.jupiter)
        "testImplementation"(libs.assertj.core)
        "testImplementation"(libs.mockk)

        // natives classifier for tests
        val gdxPlatform = libs.gdx.platform.get().module
        val gdxVer = libs.versions.gdx.get()
        "testRuntimeOnly"("$gdxPlatform:$gdxVer:natives-desktop")
    }

    // ---- Publishing to ~/.m2 ----
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = project.name
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

    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            verbose.set(true)
            outputToConsole.set(true)
            ignoreFailures.set(false)

            reporters {
                reporter(ReporterType.PLAIN)
                reporter(ReporterType.CHECKSTYLE)
            }

            filter {
                exclude("**/build/generated/**")
                exclude("**/generated/**")
                include("**/src/**/*.kt")
            }
        }

        tasks.named("build") {
            dependsOn("ktlintFormat")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

extensions.configure<EclipseModel> {
    project.name = "canopy-parent"
}

tasks.named<Delete>("clean") {
    delete(
        rootDir.walkTopDown()
            .filter { it.isDirectory && it.name == ".canopy" }
            .toList()
    )
}

fun relTask(
    name: String,
    axionTask: String,
    props: Map<String, String>
) = tasks.register<GradleBuild>(name) {
    group = "versioning"
    description = "Axion: $axionTask with ${props.entries.joinToString()}"

    tasks = listOf(axionTask)
    startParameter.projectProperties.putAll(props)
}

relTask(
    name = "alphaPatch",
    axionTask = "createRelease",
    props = mapOf(
        "release.versionIncrementer" to "incrementPatch",
        "release.prerelease" to "alpha" // or "alpha." depending on your format
    )
)

relTask(
    name = "alphaMinor",
    axionTask = "release",
    props = mapOf(
        "release.versionIncrementer" to "incrementMinor",
        "release.prerelease" to "alpha"
    )
)

relTask(
    name = "alphaMajor",
    axionTask = "release",
    props = mapOf(
        "release.versionIncrementer" to "incrementMajor",
        "release.prerelease" to "alpha"
    )
)

relTask(
    name = "rc",
    axionTask = "release",
    props = mapOf("release.prerelease" to "rc")
)

relTask(
    name = "stable",
    axionTask = "release",
    props = mapOf("release.finalize" to "true")
)

tasks.named("verifyRelease") {
    dependsOn("build")
}
