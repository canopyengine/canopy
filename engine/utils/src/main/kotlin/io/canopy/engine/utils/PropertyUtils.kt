package io.canopy.engine.utils

import java.util.Properties

fun loadClasspathProperties(name: String): Properties? = object {}.javaClass.classLoader
    .getResourceAsStream(name)
    ?.use { stream ->
        Properties().apply { load(stream) }
    }
