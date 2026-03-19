package io.canopy.engine.core

import java.util.jar.JarFile

/**
 * Provides metadata about the engine build.
 *
 * Currently, exposes information from the JAR manifest, such as the engine version.
 *
 * Behavior depends on how the application is running:
 *
 * 1) Running from a packaged JAR
 *    - The manifest is available.
 *    - Attributes like "Project-Version" can be read.
 *
 * 2) Running from an IDE / classes directory
 *    - There is usually no manifest containing custom attributes.
 *    - In this case values will fall back to defaults (e.g. `"Unknown"`).
 *
 * This object is intentionally lazy so that:
 * - manifest reading only happens if the information is actually requested
 * - startup cost stays minimal.
 */
object CanopyBuildInfo {

    /**
     * Lazily loads the manifest attributes from the running JAR (if available).
     *
     * Steps:
     * 1. Determine where this class was loaded from.
     * 2. If the location is a `.jar`, open it.
     * 3. Read the manifest's main attributes.
     *
     * If running from IDE/classes, this returns null.
     */
    private val attributes by lazy {

        // Location where this class was loaded from (JAR or classes directory).
        val url = CanopyBuildInfo::class.java.protectionDomain.codeSource?.location
            ?: return@lazy null

        val uri = url.toURI()
        val path = uri.path

        // When running from a packaged JAR, read the manifest.
        if (path.endsWith(".jar")) {
            JarFile(path).use { jar ->
                jar.manifest?.mainAttributes
            }
        } else {
            // When running from IDE/classes directory there is usually no manifest.
            null
        }
    }

    /**
     * Engine version extracted from the JAR manifest.
     *
     * Expected manifest entry:
     *
     * ```
     * Project-Version: x.y.z
     * ```
     *
     * If the manifest is unavailable (e.g. running from IDE),
     * `"Unknown"` is returned.
     */
    val projectVersion: String
        get() = attributes?.getValue("Project-Version") ?: "Unknown"
}
