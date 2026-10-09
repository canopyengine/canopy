import java.util.Properties
import javax.inject.Inject
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.component.SoftwareComponentFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    `java-gradle-plugin`
    `maven-publish`
    jacoco
}
val canopyProperties = Properties().apply { file("../../gradle.properties").inputStream().use(::load) }
group = "io.github.canopyengine"
version = canopyProperties.getProperty("canopyVersion").trim()
kotlin { jvmToolchain(17) }
java {
    withSourcesJar()
}

// Compiler and Gradle entry points run in separate hosts; keep their classes and dependencies isolated.
val compiler = sourceSets.create("compiler")
sourceSets.test {
    compileClasspath += compiler.output
    runtimeClasspath += compiler.output
}
dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    add(compiler.compileOnlyConfigurationName, libs.kotlin.compiler.embeddable)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
gradlePlugin {
    plugins {
        create("canopyCompiler") {
            id = "io.github.canopyengine.compiler"
            implementationClass = "io.canopy.tooling.compiler.gradle.CanopyCompilerPlugin"
            displayName = "Canopy compiler checks"
            description = "Installs extensible Canopy compile-time checks, including mandatory node state safety."
        }
    }
}
val coordinates = tasks.register("generateCoordinates") {
    val directory = layout.buildDirectory.dir("generated/coordinates")
    outputs.dir(directory)
    inputs.property("version", project.version.toString())
    inputs.property("kotlinVersion", libs.versions.kotlin.get())
    doLast {
        directory.get().file("io/canopy/tooling/compiler/gradle/Coordinates.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """package io.canopy.tooling.compiler.gradle

internal const val CANOPY_VERSION = "${project.version}"
internal const val CANOPY_KOTLIN_VERSION = "${libs.versions.kotlin.get()}"
"""
            )
        }
    }
}
kotlin.sourceSets.main { kotlin.srcDir(coordinates) }
tasks.named("compileKotlin") { dependsOn(coordinates) }
base { archivesName.set("canopy-compiler-gradle") }
val compilerJar = tasks.register<Jar>("compilerJar") {
    archiveBaseName.set("canopy-compiler")
    from(compiler.output)
}
val compilerSourcesJar = tasks.register<Jar>("compilerSourcesJar") {
    archiveBaseName.set("canopy-compiler")
    archiveClassifier.set("sources")
    from(compiler.allSource)
}
val compilerJavadocJar = tasks.register<Jar>("compilerJavadocJar") {
    archiveBaseName.set("canopy-compiler")
    archiveClassifier.set("javadoc")
    dependsOn("dokkaGeneratePublicationHtml")
    from(layout.buildDirectory.dir("dokka/html"))
}
tasks.assemble { dependsOn(compilerJar) }
// Composite builds select this capability instead of placing the Gradle host jar on the compiler classpath.
val compilerElements = configurations.create("compilerElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
    outgoing.capability("io.github.canopyengine:canopy-compiler-checks:${project.version}")
    outgoing.artifact(compilerJar)
}

/** Supplies Gradle's public component factory for the compiler-only Maven publication. */
abstract class CompilerComponentFactory @Inject constructor(val factory: SoftwareComponentFactory)
val compilerComponent = objects.newInstance<CompilerComponentFactory>().factory.adhoc("compiler")
components.add(compilerComponent)
compilerComponent.addVariantsFromConfiguration(compilerElements) { mapToMavenScope("runtime") }
val gradleJavadocJar = tasks.register<Jar>("gradleJavadocJar") {
    archiveClassifier.set("javadoc")
    dependsOn("dokkaGeneratePublicationHtml")
    from(layout.buildDirectory.dir("dokka/html"))
}
publishing {
    repositories {
        mavenLocal()
        maven {
            name = "verification"
            url = uri(layout.buildDirectory.dir("verification-repository"))
        }
    }
    publications {
        create<MavenPublication>("compiler") {
            artifactId = "canopy-compiler"
            from(compilerComponent)
            artifact(compilerSourcesJar)
            artifact(compilerJavadocJar)
        }
        withType<MavenPublication>().configureEach {
            if (name == "pluginMaven") {
                artifactId = "canopy-compiler-gradle"
                artifact(gradleJavadocJar)
            }
        }
    }
}
tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar, compilerJar, "publishAllPublicationsToVerificationRepository")
    systemProperty(
        "canopy.verification.repository",
        layout.buildDirectory.dir("verification-repository").get().asFile.absolutePath
    )
    systemProperty("canopy.gradle.home", gradle.gradleUserHomeDir.absolutePath)
    systemProperty("canopy.compiler.jar", compilerJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("canopy.gradle.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    doFirst {
        systemProperty("canopy.compiler.runtime", classpath.filter { it.extension == "jar" }.asPath)
    }
}

apply(from = file("../../gradle/central-publication.gradle.kts"))

// Include the compiler-host API as well as the Gradle-host entry point in published API documentation.
dokka {
    dokkaSourceSets.configureEach {
        enableJdkDocumentationLink.set(false)
        enableKotlinStdLibDocumentationLink.set(false)
    }
    dokkaSourceSets.named("main") {
        sourceRoots.from(file("src/compiler/kotlin"))
        classpath.from(compiler.compileClasspath)
    }
}
