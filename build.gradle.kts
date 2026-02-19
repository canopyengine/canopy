import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

val projectVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false

    // Apply to use "api", "implementation" and other utility methods
    id("java-library")
}

allprojects {
    apply(plugin = "eclipse")
    apply(plugin = "idea")

    group = "io.github.canopyengine"
    version = projectVersion

    extensions.configure<IdeaModel> {
        module {
            outputDir = file("build/classes/kotlin/main")
            testOutputDir = file("build/classes/kotlin/test")
        }
    }
}

configure(subprojects) {
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
        // withJavadocJar()
    }

    // generate assets.txt under /assets
    tasks.register("generateAssetList") {
        val assetsFolder = file("$rootDir/assets/")
        inputs.dir(assetsFolder)

        doLast {
            val assetsFile = File(assetsFolder, "assets.txt")
            if (assetsFile.exists()) assetsFile.delete()

            fileTree(assetsFolder)
                .files
                .map {
                    assetsFolder
                        .toPath()
                        .relativize(it.toPath())
                        .toString()
                        .replace("\\", "/")
                }
                .sorted()
                .forEach { assetsFile.appendText("$it\n") }
        }
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn("generateAssetList")
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
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
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        val libs = rootProject.libs

        // Kotlin
        api(libs.kotlin.stdlib)
        implementation(libs.kotlin.reflect)
        api(libs.coroutines.core)

        // Testing
        testImplementation(libs.kotlin.test.junit5)
        testImplementation(libs.junit.jupiter)
        testImplementation(libs.assertj.core)
        testImplementation(libs.mockk)

        // LibGDX test deps
        // testImplementation(libs.gdx.backend.headless)
        // testImplementation(libs.gdx.backend.lwjgl3)

        // natives classifier
        val gdxPlatform = libs.gdx.platform.get().module
        val gdxVer = libs.versions.gdx.get()
        testRuntimeOnly("$gdxPlatform:$gdxVer:natives-desktop")
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
                exclude("**/generated/**")
                include("**/src/**/*.kt")
            }
        }

        // Attach ktlintFormat to build so it runs automatically
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
