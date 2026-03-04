package io.canopy.engine.logging.util

import kotlin.math.roundToInt
import java.lang.management.ManagementFactory
import org.slf4j.LoggerFactory

/**
 * Prints the engine startup banner to the console.
 *
 * The banner is loaded from a resource file (`/logo-banner.txt`) and printed
 * using the logging system so that it integrates with the application's
 * logging configuration.
 *
 * Two rendering modes are supported:
 *
 * - [Mode.SIMPLE]   → Single brand color
 * - [Mode.GRADIENT] → Vertical gradient across banner lines
 *
 * The banner output includes additional runtime metadata such as:
 * - Engine version
 * - JVM version
 * - Operating system
 * - Process ID
 *
 * ANSI escape sequences are used for coloring. If the terminal does not
 * support ANSI colors, the banner will still render as plain text.
 */
object ConsoleBanner {

    enum class Mode { SIMPLE, GRADIENT }

    private const val RESOURCE = "/logo-banner.txt"

    // Brand colors
    private const val BANNER = "\u001B[38;2;56;142;60m"
    private const val DIM = "\u001B[38;2;180;180;180m"
    private const val LABEL = "\u001B[38;2;120;180;90m"
    private const val VALUE = "\u001B[38;2;255;193;7m"
    private const val RESET = "\u001B[0m"

    private val gradient: List<Triple<Int, Int, Int>> = listOf(
        Triple(56, 142, 60),
        Triple(139, 195, 74),
        Triple(255, 193, 7)
    )

    private val ansiEnabled: Boolean =
        System.console() != null && System.getenv("NO_COLOR") == null

    fun print(version: String, mode: Mode = Mode.SIMPLE) {
        val logger = LoggerFactory.getLogger("BOOT")

        val width = detectTerminalWidth(defaultWidth = 120)
        val lines = readBannerLines()

        val bannerLines: List<String> = when (mode) {
            Mode.SIMPLE -> lines.map { line ->
                "$BANNER${centerLine(line, width)}$RESET"
            }

            Mode.GRADIENT -> lines.mapIndexed { index, line ->
                if (!ansiEnabled) return@mapIndexed line

                val colored = colorizeLine(line, index, lines.size)
                // center based on visible text (without ANSI)
                val centeredPlain = centerLine(line, width)
                // re-apply the computed color to the centered plain line
                val ratio = index.toDouble() / (lines.size - 1).coerceAtLeast(1)
                val (r, g, b) = interpolateColor(ratio)
                "\u001B[38;2;$r;$g;$b" + "m$centeredPlain$RESET"
            }
        }

        val infoLine = centerLine(buildInfoLine(version), width)

        logger.info(
            buildString {
                appendLine()
                bannerLines.forEach { appendLine(it) }
                appendLine(infoLine)
                appendLine()
            }
        )
    }

    private fun readBannerLines(): List<String> = ConsoleBanner::class.java.getResourceAsStream(RESOURCE)
        ?.bufferedReader()
        ?.readLines()
        ?: error("Banner not found on classpath: $RESOURCE")

    private fun buildInfoLine(version: String): String {
        val jvm = System.getProperty("java.version")
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        val pid = ManagementFactory.getRuntimeMXBean().name.substringBefore("@")

        return buildString {
            append(DIM).append(":: ").append(RESET)
            append(VALUE).append("Canopy").append(RESET)
            append(DIM).append(" :: ").append(RESET)

            append(LABEL).append("v").append(RESET)
            append(VALUE).append(version).append(RESET)

            append(DIM).append(" | ").append(RESET)
            append(LABEL).append("JVM ").append(RESET)
            append(VALUE).append(jvm).append(RESET)

            append(DIM).append(" | ").append(RESET)
            append(LABEL).append("OS ").append(RESET)
            append(VALUE).append(os).append(RESET)

            append(DIM).append(" | ").append(RESET)
            append(LABEL).append("PID ").append(RESET)
            append(VALUE).append(pid).append(RESET)
        }
    }

    /**
     * Centers [text] within [width] columns.
     * Note: This expects plain text (no ANSI codes).
     */
    private fun centerLine(text: String, width: Int): String {
        val visible = text.length
        if (visible >= width) return text
        val padLeft = (width - visible) / 2
        return " ".repeat(padLeft) + text
    }

    /**
     * Best-effort terminal width detection.
     * - Works in many shells via $COLUMNS
     * - Falls back to [defaultWidth] (useful in CI)
     */
    private fun detectTerminalWidth(defaultWidth: Int): Int {
        val columns = System.getenv("COLUMNS")?.toIntOrNull()
        return (columns ?: defaultWidth).coerceAtLeast(40)
    }

    private fun colorizeLine(line: String, index: Int, total: Int): String {
        val ratio = index.toDouble() / (total - 1).coerceAtLeast(1)
        val (r, g, b) = interpolateColor(ratio)
        return "\u001B[38;2;$r;$g;$b" + "m$line$RESET"
    }

    private fun interpolateColor(ratio: Double): Triple<Int, Int, Int> {
        val scaled = ratio * (gradient.size - 1)
        val lowerIndex = scaled.toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(gradient.lastIndex)
        val localRatio = scaled - lowerIndex

        val (r1, g1, b1) = gradient[lowerIndex]
        val (r2, g2, b2) = gradient[upperIndex]

        return Triple(
            (r1 + (r2 - r1) * localRatio).roundToInt(),
            (g1 + (g2 - g1) * localRatio).roundToInt(),
            (b1 + (b2 - b1) * localRatio).roundToInt()
        )
    }
}
