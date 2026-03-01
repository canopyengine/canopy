
import kotlin.jvm.optionals.getOrNull
import com.github.zafarkhaja.semver.Version
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import pl.allegro.tech.build.axion.release.CreateReleaseTask

val canopyVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.axion.release) apply true
}


scmVersion {
    // Basic setup: tag versions like v1.2.3
    tag {
        prefix.set("v")
    }

    // Optional but common:
    // - localOnly avoids any remote interaction for version resolution (still creates a local tag)
    // - useful if CI has limited git remotes setup
    repository {
        // localOnly.set(true)   // uncomment if you want zero remote reads
        // pushTagsOnly.set(true) // only affects pushing; you won't push from these tasks anyway
    }

    versionCreator { version, position ->
        version
    }
}

version = scmVersion.version




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

fun hasLabel(v: Version, label: String): Boolean =
    v.preReleaseVersion().getOrNull()?.startsWith(label) == true

fun bumpLabel(v: Version, label: String): Version {
    val pre = v.preReleaseVersion().getOrNull() ?: ""
    val m = Regex("^${Regex.escape(label)}(\\d+)$").matchEntire(pre)
    val next = (m?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1
    return v.nextPreReleaseVersion("$label$next")
}

tasks.register<CreateReleaseTask>("alphaPatch") {
    group = "release"
    description = "Release next alpha for patch line (…-alphaN)."
    versionConfig = scmVersion

    doFirst {
        versionConfig.versionIncrementer { ctx ->
            val v = ctx.currentVersion as Version
            if (hasLabel(v, "alpha")) bumpLabel(v, "alpha") else v.nextPatchVersion("alpha1")
        }
    }
}

tasks.register<CreateReleaseTask>("alphaMinor") {
    group = "release"
    description = "Release next alpha for minor line (…-alphaN)."
    versionConfig = scmVersion

    doFirst {
        versionConfig.versionIncrementer { ctx ->
            val v = ctx.currentVersion as Version
            if (hasLabel(v, "alpha") && v.patchVersion() == 0L) bumpLabel(v, "alpha")
            else v.nextMinorVersion("alpha1")
        }
    }
}

tasks.register<CreateReleaseTask>("alphaMajor") {
    group = "release"
    description = "Release next alpha for major line (…-alphaN)."
    versionConfig = scmVersion

    doFirst {
        versionConfig.versionIncrementer { ctx ->
            val v = ctx.currentVersion as Version
            if (hasLabel(v, "alpha") && v.minorVersion() == 0L && v.patchVersion() == 0L) bumpLabel(v, "alpha")
            else v.nextMajorVersion("alpha1")
        }
    }
}

tasks.register<CreateReleaseTask>("rc") {
    group = "release"
    description = "Release/bump RC for the same M.m.p (…-rcN)."
    versionConfig = scmVersion

    doFirst {
        versionConfig.versionIncrementer { ctx ->
            val v = ctx.currentVersion as Version
            if (hasLabel(v, "rc")) bumpLabel(v, "rc") else v.nextPreReleaseVersion("rc1")
        }
    }
}

tasks.register<CreateReleaseTask>("stable") {
    group = "release"
    description = "Finalize current M.m.p by stripping any prerelease."
    versionConfig = scmVersion

    doFirst {
        versionConfig.versionIncrementer { ctx ->
            ctx.currentVersion.toStableVersion()
        }
    }
}

tasks.named("verifyRelease") {
    dependsOn("build")
}
