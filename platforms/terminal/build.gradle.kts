import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

dependencies {
    // Canopy core only
    implementation(projects.engine)
    implementation(projects.tooling.utils)

    // Terminal adapter ONLY (no headless, no libgdx)
    implementation(projects.adapters.mordant)

    // Devtools for testing (AppTestDriver, testHeadlessApp, etc.)
    testImplementation(projects.tooling.devtools)

    // Logging
    runtimeOnly(libs.logback.classic)

    // Test dependencies
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
}

tasks.register<JavaExec>("commandPromptSmoke") {
    group = "verification"
    description = "Launch the manual command prompt terminal smoke example"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.canopy.platforms.terminal.app.CommandPromptSmokeKt")
    standardInput = System.`in`
}

// Unpublished #171 experiment: reuse only JVM assets and prototype classes, not the terminal runtime.
val jvmHeadlessRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
dependencies {
    jvmHeadlessRuntime(projects.engine)
}
val jvmHeadlessPrototypeJar = tasks.register<Jar>("jvmHeadlessPrototypeJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("jvm-headless-experiment")
    from(sourceSets.main.get().output) {
        include("io/canopy/platforms/terminal/data/assets/**")
    }
    from(sourceSets.test.get().output) {
        include("io/canopy/platforms/terminal/experiment/JvmHeadlessHost*class")
        include("io/canopy/platforms/terminal/experiment/JvmHeadlessApp*class")
        include("io/canopy/platforms/terminal/experiment/JvmHeadlessSmoke*class")
        include("jvm-headless-world.txt")
        exclude("**/*Tests*")
    }
}
tasks.register<JavaExec>("jvmHeadlessSmoke") {
    group = "verification"
    description = "Run unpublished JVM headless prototype without graphics or terminal dependencies"
    dependsOn(jvmHeadlessPrototypeJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    timeout.set(Duration.ofSeconds(30))
    classpath = files(jvmHeadlessPrototypeJar.flatMap { it.archiveFile }) + jvmHeadlessRuntime
    mainClass.set("io.canopy.platforms.terminal.experiment.JvmHeadlessSmokeKt")
}
tasks.register("jvmHeadlessDependencyReport") {
    group = "verification"
    description = "Record actual headless, terminal and isolated JVM experiment runtime artifacts"
    dependsOn(jvmHeadlessPrototypeJar)
    val output = layout.buildDirectory.file("jvm-headless/runtime-graphs.tsv")
    outputs.file(output)
    // Resolve each graph afresh so the report cannot outlive a dependency change.
    outputs.upToDateWhen { false }
    doLast {
        val graphs = linkedMapOf(
            "headless" to project(":platforms:headless").configurations.getByName("runtimeClasspath"),
            "terminal" to configurations.getByName("runtimeClasspath"),
            "experiment" to jvmHeadlessRuntime
        )
        val rows = mutableListOf("graph\tcomponent\tartifact\tfile")
        graphs.forEach { (name, graph) ->
            graph.incoming.artifacts.artifacts.sortedBy { it.file.name }.forEach { artifact ->
                rows +=
                    "$name\t${artifact.id.componentIdentifier.displayName}\t${artifact.id.displayName}\t${artifact.file.name}"
            }
        }
        rows +=
            "experiment\tselected prototype and JVM asset classes\tunpublished experiment JAR\t${jvmHeadlessPrototypeJar.get().archiveFileName.get()}"
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(rows.joinToString("\n", postfix = "\n"))
        }
    }
}
