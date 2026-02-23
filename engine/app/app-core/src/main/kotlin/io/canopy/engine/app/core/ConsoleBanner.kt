package io.canopy.engine.app.core

import kotlin.math.roundToInt
import java.lang.management.ManagementFactory
import org.slf4j.LoggerFactory

object ConsoleBanner {

    enum class Mode { SIMPLE, GRADIENT }

    private const val RESOURCE = "/logo-banner.txt"

    // Brand colors
    private const val BANNER = "\u001B[38;2;56;142;60m" // canopy green (simple mode)
    private const val DIM = "\u001B[38;2;180;180;180m"
    private const val LABEL = "\u001B[38;2;120;180;90m" // soft leaf green
    private const val VALUE = "\u001B[38;2;255;193;7m" // golden
    private const val RESET = "\u001B[0m"

    // Gradient for banner (top -> bottom)
    private val gradient: List<Triple<Int, Int, Int>> = listOf(
        Triple(56, 142, 60), // deep canopy green
        Triple(139, 195, 74), // light leaf green
        Triple(255, 193, 7) // golden bird yellow
    )

    fun print(version: String, mode: Mode = Mode.SIMPLE) {
        val logger = LoggerFactory.getLogger("BOOT")

        val bannerText = when (mode) {
            Mode.SIMPLE -> "$BANNER${readBannerText()}$RESET"
            Mode.GRADIENT -> colorizeBanner(readBannerLines())
        }

        val infoLine = buildInfoLine(version)

        logger.info(
            buildString {
                appendLine()
                appendLine(bannerText)
                appendLine(infoLine)
                appendLine()
            }
        )
    }

    private fun readBannerText(): String = ConsoleBanner::class.java.getResourceAsStream(RESOURCE)
        ?.bufferedReader()
        ?.readText()
        ?: error("Banner not found on classpath: $RESOURCE")

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

    private fun colorizeBanner(lines: List<String>): String = lines.mapIndexed { index, line ->
        colorizeLine(line, index, lines.size)
    }.joinToString("\n")

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
