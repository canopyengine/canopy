package io.canopy.engine.data.assets

/** File resolution locations; roots and availability depend on the active asset backend. */
enum class FileSource {
    Internal,
    External,
    Classpath,
    Local,
    Absolute,
}
