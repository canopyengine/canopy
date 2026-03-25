import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel

val canopyVersion: String by project

plugins {
    base
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
