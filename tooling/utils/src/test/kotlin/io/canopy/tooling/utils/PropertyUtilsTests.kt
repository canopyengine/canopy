package io.canopy.tooling.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertyUtilsTests {

    @Test
    fun `loadClasspathProperties returns properties when resource exists`() {
        val properties = loadClasspathProperties("sample-test.properties")

        assertEquals("canopy", properties?.getProperty("name"))
        assertEquals("90", properties?.getProperty("target.coverage"))
    }

    @Test
    fun `loadClasspathProperties returns null when resource is missing`() {
        assertNull(loadClasspathProperties("missing-resource.properties"))
    }
}
