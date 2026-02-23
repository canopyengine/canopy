package io.canopy.engine.core

object CanopyBuildInfo {
    val version: String by lazy {
        // Reads Implementation-Version from the jar manifest when packaged.
        val pkg = CanopyBuildInfo::class.java.`package`
        pkg?.implementationVersion ?: "unknown"
    }
}
