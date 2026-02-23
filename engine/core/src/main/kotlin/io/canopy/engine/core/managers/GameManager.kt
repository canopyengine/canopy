package io.canopy.engine.core.managers

object GameManager {
    var executionMode: ExecutionMode = ExecutionMode.Normal
    fun onDebugMode() = executionMode == ExecutionMode.Debug
}

enum class ExecutionMode {
    Normal,
    Debug,
}
