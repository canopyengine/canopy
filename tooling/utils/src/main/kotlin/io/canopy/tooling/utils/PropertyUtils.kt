package io.canopy.tooling.utils

import java.util.*

fun loadClasspathProperties(name: String): Properties? = object {}.javaClass.classLoader
    .getResourceAsStream(name)
    ?.use { stream ->
        Properties().apply { load(stream) }
    }
