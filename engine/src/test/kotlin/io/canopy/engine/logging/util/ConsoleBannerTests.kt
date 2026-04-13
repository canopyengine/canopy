package io.canopy.engine.logging.util

import kotlin.test.Test

class ConsoleBannerTests {

    @Test
    fun `prints simple mode`() {
        ConsoleBanner.print("1.0.0", ConsoleBanner.Mode.SIMPLE)
    }

    @Test
    fun `prints gradient mode`() {
        ConsoleBanner.print("1.0.0", ConsoleBanner.Mode.GRADIENT)
    }

    @Test
    fun `private color methods work correctly`() {
        val interpolateMethod = ConsoleBanner::class.java.getDeclaredMethod("interpolateColor", Double::class.java)
        interpolateMethod.isAccessible = true
        val result = interpolateMethod.invoke(ConsoleBanner, 0.5)

        val colorizeMethod = ConsoleBanner::class.java.getDeclaredMethod(
            "colorizeLine",
            String::class.java,
            Int::class.java,
            Int::class.java
        )
        colorizeMethod.isAccessible = true
        colorizeMethod.invoke(ConsoleBanner, "test", 1, 10)
    }
}
