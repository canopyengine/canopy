import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `maven-publish`
    jacoco
}
val canopyProperties = Properties().apply { file("../../gradle.properties").inputStream().use(::load) }
group = "io.canopy"
version = canopyProperties.getProperty("canopyVersion").trim()
kotlin { jvmToolchain(17) }
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
    doFirst { systemProperty("canopy.compiler.runtime", classpath.filter { it.extension == "jar" }.asPath) }
    systemProperty("canopy.compiler.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}
publishing {
    repositories { mavenLocal() }
    publications { create<MavenPublication>("compiler") { from(components["java"]) } }
}
