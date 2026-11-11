package io.canopy.engine.graphics

/** Grid decoration drawn by the platform without changing logical cell coordinates or contents. */
enum class GridStyle {
    /** Compact cells, initially shown as neutral dots without connecting lines. */
    Dotted,

    /** Connected lines enclosing every cell; initially empty cells are blank. */
    Solid,
}
