package io.canopy.tooling.diagnostics

import kotlin.test.*

class DiagnosticFormatTests {
    @Test
    fun `plain diagnostic has exact source location explanation and a readable edit`() {
        // Arrange
        val diagnostic = Diagnostic(
            "CANOPY_PROPERTY_DELEGATION",
            "Use `by` for a computed property",
            SourceLine("nodes/Aquarium.kt", 7, 17, "    val fishNum = computed {"),
            label = "`=` stores the Computed object.",
            help = "Replace `=` with `by`.",
            replacement = "by"
        )

        // Act / Assert
        assertEquals(
            """
            error[CANOPY_PROPERTY_DELEGATION]: Use `by` for a computed property
              --> nodes/Aquarium.kt:7:17
              |
            7 |     val fishNum = computed {
              |                 ^ `=` stores the Computed object.

            help: Replace `=` with `by`.
            7 -     val fishNum = computed {
            7 +     val fishNum by computed {
            """.trimIndent(),
            DiagnosticFormat.render(diagnostic)
        )
    }

    @Test
    fun `colors preserve plain meaning and distinguish warnings additions and removals`() {
        // Arrange
        val diagnostic = Diagnostic(
            "C",
            "Fix this",
            SourceLine("file.kt", 1, 1, "="),
            help = "Use by",
            replacement = "by",
            warning = true
        )

        // Act
        val colored = DiagnosticFormat.render(diagnostic, color = true)

        // Assert
        assertTrue("\u001B[1;33mwarning" in colored)
        assertTrue("\u001B[31m1 - =" in colored)
        assertTrue("\u001B[32m1 + by" in colored)
        assertEquals(DiagnosticFormat.render(diagnostic), colored.replace(Regex("\u001B\\[[0-9;]+m"), ""))
    }

    @Test
    fun `tabs unicode windows paths and control characters remain readable`() {
        // Arrange
        val diagnostic = Diagnostic(
            "C",
            "Bad\u001B[31m title",
            SourceLine("C:\\game\\Água.kt", 3, 12, "\tval café = value\u001B[2J"),
            label = "fix here",
            help = "help"
        )

        // Act
        val result = DiagnosticFormat.render(diagnostic)

        // Assert
        assertTrue("C:\\game\\Água.kt:3:12" in result)
        assertFalse('\t' in result)
        assertFalse('\u001B' in result)
        assertTrue("\\u001b" in result)
        assertTrue("café" in result)
    }

    @Test
    fun `long lines and fixes stay bounded and missing source needs no fake excerpt`() {
        // Arrange
        val long = Diagnostic(
            "C",
            "Fix",
            SourceLine("file.kt", 1, 501, "x".repeat(500) + "=" + "x".repeat(500)),
            replacement = "by",
            help = "Replace ="
        )

        // Act / Assert
        val result = DiagnosticFormat.render(long)
        assertTrue(result.lines().all { it.length < 150 }, result)
        assertTrue("..." in result)
        val noSource = DiagnosticFormat.render(Diagnostic("C", "Build mismatch", help = "Rebuild both artifacts"))
        assertEquals("error[C]: Build mismatch\n\nhelp: Rebuild both artifacts", noSource)
    }

    @Test
    fun `tabs retain their source columns across highlights and clipped excerpts`() {
        // Arrange
        val lines = listOf("\tval\tfishNum\t=\tcomputed { 1 }", "x".repeat(119) + "\t=\tvalue")
        lines.forEach { line ->
            val diagnostic = Diagnostic(
                "C",
                "Fix",
                SourceLine("Game.kt", 2, line.indexOf('=') + 1, line),
                replacement = "by"
            )

            // Act
            val plain = DiagnosticFormat.render(diagnostic)
            val colored = DiagnosticFormat.render(diagnostic, color = true)
                .replace(Regex("\u001B\\[[0-9;]+m"), "")

            // Assert
            assertEquals(plain, colored)
            assertTrue("=   " in plain, plain)
            assertEquals(
                plain.lineSequence().first { it.startsWith("2 | ") }.removePrefix("2 | "),
                plain.lineSequence().first { it.startsWith("2 - ") }.removePrefix("2 - ")
            )
        }
        val tab = Diagnostic("C", "Fix", SourceLine("Game.kt", 1, 2, "x\t= value"))
        assertTrue("1 | x   = value\n  |  ^^^" in DiagnosticFormat.render(tab))
    }

    @Test
    fun `incomplete or multiline edits keep help without displaying a misleading correction`() {
        // Arrange
        val source = SourceLine("Game.kt", 1, 1, "::Fish")
        val edits = listOf(
            Diagnostic("C", "Fix", source.copy(length = 20), replacement = "{ Fish() }", help = "Use a lambda"),
            Diagnostic("C", "Fix", source, replacement = "{\nFish()\n}", help = "Use a lambda"),
            Diagnostic("C", "Fix", source.copy(column = 0), replacement = "by", help = "Use a delegate")
        )

        // Act / Assert
        edits.forEach { diagnostic ->
            val output = DiagnosticFormat.render(diagnostic)
            assertTrue("help:" in output, output)
            assertFalse("1 +" in output || "1 -" in output, output)
        }
    }

    @Test
    fun `automatic color respects plain console no color ci and ide signals`() {
        fun enabled(
            mode: String = "auto",
            rich: Boolean = true,
            plain: Boolean = false,
            noColor: Boolean = false,
            ci: Boolean = false,
            ide: Boolean = false,
        ) = DiagnosticColor.enabled(mode, rich, plain, noColor, ci, ide)
        assertTrue(enabled())
        assertFalse(enabled(rich = false))
        assertFalse(enabled(plain = true))
        assertFalse(enabled(noColor = true))
        assertFalse(enabled(ci = true))
        assertFalse(enabled(ide = true))
        assertFalse(enabled("never"))
        assertTrue(enabled("always", ci = true))
        assertFalse(enabled("always", plain = true))
        assertFailsWith<IllegalArgumentException> { enabled("invalid") }
    }
}
