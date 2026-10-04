package io.canopy.tooling.nodes

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/** Consumer-facing plugin: validates all Kotlin compilations, including test and indirect node subclasses. */
class CanopyNodePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) = Unit
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
    override fun getCompilerPluginId(): String = "io.canopy.node-state"
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact("io.canopy", "engine-compiler", CANOPY_VERSION)
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val version = project.getKotlinPluginVersion()
        if (version != CANOPY_KOTLIN_VERSION) {
            throw GradleException(
                "Canopy $CANOPY_VERSION node checks require Kotlin $CANOPY_KOTLIN_VERSION; " +
                    "${project.path} uses $version"
            )
        }
        return project.provider { emptyList() }
    }
}
