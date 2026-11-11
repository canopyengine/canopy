import org.gradle.api.tasks.SourceSetContainer

allprojects {
    plugins.withId("java") {
        if (project.path == ":platforms:terminal") {
            val sources = extensions.getByType<SourceSetContainer>()
            tasks.register("benchmarkClasspath") {
                dependsOn("classes")
                doLast {
                    layout.buildDirectory.file("benchmark-classpath.txt").get().asFile
                        .writeText(sources.named("main").get().runtimeClasspath.asPath)
                }
            }
        }
    }
}
