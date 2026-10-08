package io.canopy.engine.input.binds

import kotlinx.serialization.Serializable

/** Backend-independent physical bindings; code values are Canopy identifiers, not native backend key codes. */
@Serializable
enum class InputBind private constructor(
    /** Physical device category used when polling this binding. */
    val type: Type,
    /** Stable Canopy identifier; existing names and codes remain compatible with saved configurations. */
    val code: Int,
    /** Canonical keyboard identity, or null for mouse buttons. */
    val key: Key?,
) {
    // Letters
    A(Key.A),
    B(Key.B),
    C(Key.C),
    D(Key.D),
    E(Key.E),
    F(Key.F),
    G(Key.G),
    H(Key.H),
    I(Key.I),
    J(Key.J),
    K(Key.K),
    L(Key.L),
    M(Key.M),
    N(Key.N),
    O(Key.O),
    P(Key.P),
    Q(Key.Q),
    R(Key.R),
    S(Key.S),
    T(Key.T),
    U(Key.U),
    V(Key.V),
    W(Key.W),
    X(Key.X),
    Y(Key.Y),
    Z(Key.Z),

    // Top-row digits
    NUM_0(Key.NUM_0),
    NUM_1(Key.NUM_1),
    NUM_2(Key.NUM_2),
    NUM_3(Key.NUM_3),
    NUM_4(Key.NUM_4),
    NUM_5(Key.NUM_5),
    NUM_6(Key.NUM_6),
    NUM_7(Key.NUM_7),
    NUM_8(Key.NUM_8),
    NUM_9(Key.NUM_9),

    // Arrows
    LEFT(Key.LEFT),
    RIGHT(Key.RIGHT),
    UP(Key.UP),
    DOWN(Key.DOWN),

    // Common controls
    SPACE(Key.SPACE),
    ENTER(Key.ENTER),
    ESCAPE(Key.ESCAPE),
    TAB(Key.TAB),
    BACKSPACE(Key.BACKSPACE),
    INSERT(Key.INSERT),
    DELETE(Key.DELETE),
    HOME(Key.HOME),
    END(Key.END),
    PAGE_UP(Key.PAGE_UP),
    PAGE_DOWN(Key.PAGE_DOWN),

    // Modifiers
    SHIFT_LEFT(Key.SHIFT_LEFT),
    SHIFT_RIGHT(Key.SHIFT_RIGHT),
    CTRL_LEFT(Key.CTRL_LEFT),
    CTRL_RIGHT(Key.CTRL_RIGHT),
    ALT_LEFT(Key.ALT_LEFT),
    ALT_RIGHT(Key.ALT_RIGHT),
    META_LEFT(Key.META_LEFT),
    META_RIGHT(Key.META_RIGHT),
    CAPS_LOCK(Key.CAPS_LOCK),
    NUM_LOCK(Key.NUM_LOCK),
    SCROLL_LOCK(Key.SCROLL_LOCK),
    PRINT_SCREEN(Key.PRINT_SCREEN),
    PAUSE(Key.PAUSE),

    // Punctuation / symbols
    GRAVE(Key.GRAVE),
    MINUS(Key.MINUS),
    EQUALS(Key.EQUALS),
    LEFT_BRACKET(Key.LEFT_BRACKET),
    RIGHT_BRACKET(Key.RIGHT_BRACKET),
    BACKSLASH(Key.BACKSLASH),
    SEMICOLON(Key.SEMICOLON),
    APOSTROPHE(Key.APOSTROPHE),
    COMMA(Key.COMMA),
    PERIOD(Key.PERIOD),
    SLASH(Key.SLASH),

    // Function keys
    F1(Key.F1),
    F2(Key.F2),
    F3(Key.F3),
    F4(Key.F4),
    F5(Key.F5),
    F6(Key.F6),
    F7(Key.F7),
    F8(Key.F8),
    F9(Key.F9),
    F10(Key.F10),
    F11(Key.F11),
    F12(Key.F12),

    // Numpad
    NUMPAD_0(Key.NUMPAD_0),
    NUMPAD_1(Key.NUMPAD_1),
    NUMPAD_2(Key.NUMPAD_2),
    NUMPAD_3(Key.NUMPAD_3),
    NUMPAD_4(Key.NUMPAD_4),
    NUMPAD_5(Key.NUMPAD_5),
    NUMPAD_6(Key.NUMPAD_6),
    NUMPAD_7(Key.NUMPAD_7),
    NUMPAD_8(Key.NUMPAD_8),
    NUMPAD_9(Key.NUMPAD_9),
    NUMPAD_ADD(Key.NUMPAD_ADD),
    NUMPAD_SUBTRACT(Key.NUMPAD_SUBTRACT),
    NUMPAD_MULTIPLY(Key.NUMPAD_MULTIPLY),
    NUMPAD_DIVIDE(Key.NUMPAD_DIVIDE),
    NUMPAD_DECIMAL(Key.NUMPAD_DECIMAL),
    NUMPAD_ENTER(Key.NUMPAD_ENTER),

    // Mouse
    LEFT_MOUSE(Type.Mouse, 1000, null),
    RIGHT_MOUSE(Type.Mouse, 1001, null),
    MIDDLE_MOUSE(Type.Mouse, 1002, null),
    BACK_MOUSE(Type.Mouse, 1003, null),
    FORWARD_MOUSE(Type.Mouse, 1004, null),
    ;

    private constructor(key: Key) : this(Type.Keyboard, requireNotNull(key.code), key)

    /** The physical device category used when polling a binding. */
    @Serializable
    enum class Type {
        Keyboard,
        Mouse,
    }

    companion object {
        /** Looks up a binding name ignoring case; throws when no binding matches. */
        fun from(code: String) = entries.first { it.name.equals(code, true) }
    }
}

/** Returns the canonical keyboard identity; mouse buttons have no keyboard identity and return [Key.UNKNOWN]. */
fun InputBind.toKey(): Key = key ?: Key.UNKNOWN

/** Returns the physical keyboard binding, or null for unsided modifiers and [Key.UNKNOWN]. */
fun Key.toInputBind(): InputBind? = InputBind.entries.firstOrNull { it.key == this }
