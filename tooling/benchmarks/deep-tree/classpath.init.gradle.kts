import org.gradle.api.tasks.SourceSetContainer
allprojects {
    plugins.withId("java") {
        if (project.path == ":engine") {
            tasks.register("deepTreeClasspath") {
                doLast {
                    val sources = project.extensions.getByType<SourceSetContainer>()
                    file(System.getProperty("deepTree.classpathOutput"))
                        .writeText(sources.named("main").get().runtimeClasspath.asPath)
                }
            }
        }
    }
}
