package io.canopy.adapters.logback

import java.nio.file.Path

/** Resolve an explicitly requested per-user directory; working-directory defaults never guess installation mode. */
internal fun installedLogDirectory(
    publisher: String,
    game: String,
    osName: String = System.getProperty("os.name"),
    environment: (String) -> String? = System::getenv,
    userHome: Path = Path.of(System.getProperty("user.home")),
): Path {
    requireLogDirectoryName(publisher)
    requireLogDirectoryName(game)
    fun environmentPath(name: String): Path? = environment(name)?.takeIf { it.isNotBlank() }?.let {
        Path.of(it).takeIf(Path::isAbsolute)
    }
    val os = osName.lowercase()
    val base = when {
        "mac" in os || "darwin" in os -> userHome.resolve("Library/Logs")
        "windows" in os -> environmentPath("LOCALAPPDATA") ?: userHome.resolve("AppData/Local")
        else -> environmentPath("XDG_STATE_HOME") ?: userHome.resolve(".local/state")
    }
    val directory = base.resolve(publisher).resolve(game)
    return if ("mac" in os || "darwin" in os) directory else directory.resolve("logs")
}

/** Reject path traversal and names that alias another directory on supported desktop filesystems. */
internal fun requireLogDirectoryName(name: String) {
    require(
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            name == name.trim() &&
            !name.endsWith('.') &&
            name.none { it.code < 32 || it in "<>:\"/\\|?*" } &&
            name.substringBefore('.').uppercase() !in WINDOWS_DEVICE_NAMES
    ) { "Log directory names must be single portable directory names" }
}

private val WINDOWS_DEVICE_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
    (1..9).flatMap { listOf("COM$it", "LPT$it") }
