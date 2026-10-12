package io.canopy.engine.ui

/** Portable terminal/font color. Null in an appearance inherits; Default explicitly resets the color. */
sealed interface UiColor {
    /** Uses the host's default foreground or background. */
    data object Default : UiColor

    /** Opaque RGB channels in the inclusive range 0..255. */
    data class Rgb(val red: Int, val green: Int, val blue: Int) : UiColor {
        init {
            require(red in 0..255 && green in 0..255 && blue in 0..255)
        }
    }
}

/**
 * Visual text/background properties. Null inherits; false explicitly disables an inherited emphasis.
 * These properties do not change measurement. Background colors fill the node's allocated rectangle.
 */
data class UiAppearance(
    val foreground: UiColor? = null,
    val background: UiColor? = null,
    val bold: Boolean? = null,
    val dim: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val inverse: Boolean? = null,
) {
    /** Applies explicit properties in [overrides], retaining inherited properties where null. */
    fun merge(overrides: UiAppearance): UiAppearance = if (overrides ==
        Empty
    ) {
        this
    } else if (this == Empty) {
        overrides
    } else {
        UiAppearance(
            overrides.foreground ?: foreground,
            overrides.background ?: background,
            overrides.bold ?: bold,
            overrides.dim ?: dim,
            overrides.italic ?: italic,
            overrides.underline ?: underline,
            overrides.inverse ?: inverse
        )
    }

    companion object {
        /** No explicit overrides. */
        val Empty = UiAppearance()
    }
}

/** One logical unit of border on each edge; None contributes no border inset. */
enum class UiBorder { None, Rounded, Square, Double }

/**
 * Component defaults inherited from the nearest node with a theme. Explicit appearances override
 * component defaults; focused/disabled button appearances apply last. A theme replaces the inherited
 * theme as a whole. Copy an existing theme to change just one default.
 */
data class UiTheme(
    val text: UiAppearance = UiAppearance(),
    val button: UiAppearance = UiAppearance(foreground = UiColor.Rgb(0, 205, 205)),
    val focusedButton: UiAppearance = UiAppearance(bold = true, inverse = true),
    val disabledButton: UiAppearance = UiAppearance(foreground = UiColor.Default, dim = true),
    val buttonBorder: UiBorder = UiBorder.Rounded,
) {
    companion object {
        /** Default terminal presentation: cyan actions, focused emphasis and dim disabled actions. */
        val Default = UiTheme()
    }
}
