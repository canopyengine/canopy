package io.canopy.tooling.utils

/** Marks APIs that require explicit opt-in because their contracts may change incompatibly. */
@RequiresOptIn(
    "This class is in an unstable version - using it may result in breaking changes",
    RequiresOptIn.Level.ERROR
)
annotation class UnstableApi
