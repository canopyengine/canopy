package io.canopy.engine.core.exceptions

/** An application startup call was compiled without the required Canopy compiler support. */
class CompilerSetupException :
    CanopyException(
        """
    CANOPY_COMPILER_REQUIRED: Canopy compiler support is missing from this application startup.

    Apply id("io.github.canopyengine.compiler") to the Kotlin module containing launch() or launchAsync().
    Use matching engine and compiler versions, then clean and rebuild that module.
    For other build tools, enable the matching Canopy Kotlin compiler plugin.
        """.trimIndent()
    )
