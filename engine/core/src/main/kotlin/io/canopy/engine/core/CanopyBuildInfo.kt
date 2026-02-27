package io.canopy.engine.core

import java.util.jar.JarFile

object CanopyBuildInfo {

    private val attributes by lazy {
        // Where this class was loaded from (jar or classes dir)
        val url = CanopyBuildInfo::class.java.protectionDomain.codeSource?.location
            ?: return@lazy null

        val uri = url.toURI()
        val path = uri.path

        // If running from a jar
        if (path.endsWith(".jar")) {
            JarFile(path).use { jar ->
                jar.manifest?.mainAttributes
            }
        } else {
            // Running from IDE/classes dir: there is often no manifest with your attributes
            null
        }
    }

    val projectVersion: String
        get() = attributes?.getValue("Project-Version") ?: "Unknown"
}
