import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.SigningExtension

// Shared by runtime modules and the separately hosted compiler/Gradle tooling build.
apply(plugin = "signing")

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Canopy ${project.name}")
            description.set("Kotlin-first Canopy game engine: ${project.name}.")
            url.set("https://github.com/canopyengine/canopy")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("canopyengine")
                    name.set("Canopy contributors")
                    url.set("https://github.com/canopyengine")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/canopyengine/canopy.git")
                developerConnection.set("scm:git:ssh://git@github.com/canopyengine/canopy.git")
                url.set("https://github.com/canopyengine/canopy")
            }
        }
    }
    repositories.maven {
        name = "centralStaging"
        // This is a local Maven layout, never a remote upload destination.
        url = uri(if (rootProject.name == "canopy-compiler") file("../../build/central-repository")
                  else rootProject.layout.buildDirectory.dir("central-repository").get().asFile)
    }
}

val signingKey = providers.environmentVariable("MAVEN_SIGNING_KEY")
val signingPassword = providers.environmentVariable("MAVEN_SIGNING_PASSWORD")
extensions.configure<SigningExtension> {
    isRequired = false // Local development and unsigned bundle verification need no credentials.
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
    }
    sign(extensions.getByType<PublishingExtension>().publications)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        if (repository.name == "centralStaging") {
            check(!project.version.toString().endsWith("SNAPSHOT")) {
                "Central prereleases must use immutable non-SNAPSHOT versions."
            }
            if (providers.gradleProperty("requireSigning").orNull == "true") {
                check(signingKey.isPresent) { "MAVEN_SIGNING_KEY is required for a release bundle." }
            }
        }
    }
}

// Ship the actual dual-license terms with every binary/source/documentation archive.
val licenseRoot = if (rootProject.name == "canopy-compiler") file("../..") else rootProject.projectDir
tasks.withType<Jar>().configureEach {
    from(listOf(licenseRoot.resolve("LICENSE-MIT"), licenseRoot.resolve("LICENSE-APACHE"))) {
        into("META-INF")
    }
}
