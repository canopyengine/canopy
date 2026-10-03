package io.canopy.engine.input.binds

import kotlinx.serialization.Serializable

/** Backend-independent physical bindings; code values are Canopy identifiers, not native backend key codes. */
@Serializable
enum class InputBind(val type: Type, val code: Int) {
    // Letters
    A(Type.Keyboard, 1),
    B(Type.Keyboard, 2),
    C(Type.Keyboard, 3),
    D(Type.Keyboard, 4),
    E(Type.Keyboard, 5),
    F(Type.Keyboard, 6),
    G(Type.Keyboard, 7),
    H(Type.Keyboard, 8),
    I(Type.Keyboard, 9),
    J(Type.Keyboard, 10),
    K(Type.Keyboard, 11),
    L(Type.Keyboard, 12),
    M(Type.Keyboard, 13),
    N(Type.Keyboard, 14),
    O(Type.Keyboard, 15),
    P(Type.Keyboard, 16),
    Q(Type.Keyboard, 17),
    R(Type.Keyboard, 18),
    S(Type.Keyboard, 19),
    T(Type.Keyboard, 20),
    U(Type.Keyboard, 21),
    V(Type.Keyboard, 22),
    W(Type.Keyboard, 23),
    X(Type.Keyboard, 24),
    Y(Type.Keyboard, 25),
    Z(Type.Keyboard, 26),

    // Top-row digits
    NUM_0(Type.Keyboard, 100),
    NUM_1(Type.Keyboard, 101),
    NUM_2(Type.Keyboard, 102),
    NUM_3(Type.Keyboard, 103),
    NUM_4(Type.Keyboard, 104),
    NUM_5(Type.Keyboard, 105),
    NUM_6(Type.Keyboard, 106),
    NUM_7(Type.Keyboard, 107),
    NUM_8(Type.Keyboard, 108),
    NUM_9(Type.Keyboard, 109),

    // Arrows
    LEFT(Type.Keyboard, 200),
    RIGHT(Type.Keyboard, 201),
    UP(Type.Keyboard, 202),
    DOWN(Type.Keyboard, 203),

    // Common controls
    SPACE(Type.Keyboard, 300),
    ENTER(Type.Keyboard, 301),
    ESCAPE(Type.Keyboard, 302),
    TAB(Type.Keyboard, 303),
    BACKSPACE(Type.Keyboard, 304),
    INSERT(Type.Keyboard, 305),
    DELETE(Type.Keyboard, 306),
    HOME(Type.Keyboard, 307),
    END(Type.Keyboard, 308),
    PAGE_UP(Type.Keyboard, 309),
    PAGE_DOWN(Type.Keyboard, 310),

    // Modifiers
    SHIFT_LEFT(Type.Keyboard, 400),
    SHIFT_RIGHT(Type.Keyboard, 401),
    CTRL_LEFT(Type.Keyboard, 402),
    CTRL_RIGHT(Type.Keyboard, 403),
    ALT_LEFT(Type.Keyboard, 404),
    ALT_RIGHT(Type.Keyboard, 405),
    META_LEFT(Type.Keyboard, 406),
    META_RIGHT(Type.Keyboard, 407),
    CAPS_LOCK(Type.Keyboard, 408),
    NUM_LOCK(Type.Keyboard, 409),
    SCROLL_LOCK(Type.Keyboard, 410),
    PRINT_SCREEN(Type.Keyboard, 411),
    PAUSE(Type.Keyboard, 412),

    // Punctuation / symbols
    GRAVE(Type.Keyboard, 500),
    MINUS(Type.Keyboard, 501),
    EQUALS(Type.Keyboard, 502),
    LEFT_BRACKET(Type.Keyboard, 503),
    RIGHT_BRACKET(Type.Keyboard, 504),
    BACKSLASH(Type.Keyboard, 505),
    SEMICOLON(Type.Keyboard, 506),
    APOSTROPHE(Type.Keyboard, 507),
    COMMA(Type.Keyboard, 508),
    PERIOD(Type.Keyboard, 509),
    SLASH(Type.Keyboard, 510),

    // Function keys
    F1(Type.Keyboard, 600),
    F2(Type.Keyboard, 601),
    F3(Type.Keyboard, 602),
    F4(Type.Keyboard, 603),
    F5(Type.Keyboard, 604),
    F6(Type.Keyboard, 605),
    F7(Type.Keyboard, 606),
    F8(Type.Keyboard, 607),
    F9(Type.Keyboard, 608),
    F10(Type.Keyboard, 609),
    F11(Type.Keyboard, 610),
    F12(Type.Keyboard, 611),

    // Numpad
    NUMPAD_0(Type.Keyboard, 700),
    NUMPAD_1(Type.Keyboard, 701),
    NUMPAD_2(Type.Keyboard, 702),
    NUMPAD_3(Type.Keyboard, 703),
    NUMPAD_4(Type.Keyboard, 704),
    NUMPAD_5(Type.Keyboard, 705),
    NUMPAD_6(Type.Keyboard, 706),
    NUMPAD_7(Type.Keyboard, 707),
    NUMPAD_8(Type.Keyboard, 708),
    NUMPAD_9(Type.Keyboard, 709),
    NUMPAD_ADD(Type.Keyboard, 710),
    NUMPAD_SUBTRACT(Type.Keyboard, 711),
    NUMPAD_MULTIPLY(Type.Keyboard, 712),
    NUMPAD_DIVIDE(Type.Keyboard, 713),
    NUMPAD_DECIMAL(Type.Keyboard, 714),
    NUMPAD_ENTER(Type.Keyboard, 715),

    // Mouse
    LEFT_MOUSE(Type.Mouse, 1000),
    RIGHT_MOUSE(Type.Mouse, 1001),
    MIDDLE_MOUSE(Type.Mouse, 1002),
    BACK_MOUSE(Type.Mouse, 1003),
    FORWARD_MOUSE(Type.Mouse, 1004),
    ;

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

/**
 * Maps InputBind to Key enum for KeyInputEvent creation.
 */
fun InputBind.toKey(): Key = when (this) {
    InputBind.A -> Key.A_KEY
    InputBind.B -> Key.B_KEY
    InputBind.C -> Key.C_KEY
    InputBind.D -> Key.D_KEY
    InputBind.E -> Key.E_KEY
    InputBind.F -> Key.F_KEY
    InputBind.G -> Key.G_KEY
    InputBind.H -> Key.H_KEY
    InputBind.I -> Key.I_KEY
    InputBind.J -> Key.J_KEY
    InputBind.K -> Key.K_KEY
    InputBind.L -> Key.L_KEY
    InputBind.M -> Key.M_KEY
    InputBind.N -> Key.N_KEY
    InputBind.O -> Key.O_KEY
    InputBind.P -> Key.P_KEY
    InputBind.Q -> Key.Q_KEY
    InputBind.R -> Key.R_KEY
    InputBind.S -> Key.S_KEY
    InputBind.T -> Key.T_KEY
    InputBind.U -> Key.U_KEY
    InputBind.V -> Key.V_KEY
    InputBind.W -> Key.W_KEY
    InputBind.X -> Key.X_KEY
    InputBind.Y -> Key.Y_KEY
    InputBind.Z -> Key.Z_KEY

    InputBind.NUM_0 -> Key.UNKNOWN
    InputBind.NUM_1 -> Key.UNKNOWN
    InputBind.NUM_2 -> Key.UNKNOWN
    InputBind.NUM_3 -> Key.UNKNOWN
    InputBind.NUM_4 -> Key.UNKNOWN
    InputBind.NUM_5 -> Key.UNKNOWN
    InputBind.NUM_6 -> Key.UNKNOWN
    InputBind.NUM_7 -> Key.UNKNOWN
    InputBind.NUM_8 -> Key.UNKNOWN
    InputBind.NUM_9 -> Key.UNKNOWN

    InputBind.LEFT -> Key.LEFT
    InputBind.RIGHT -> Key.RIGHT
    InputBind.UP -> Key.UP
    InputBind.DOWN -> Key.DOWN

    InputBind.SPACE -> Key.SPACE
    InputBind.ENTER -> Key.ENTER
    InputBind.ESCAPE -> Key.ESCAPE
    InputBind.BACKSPACE -> Key.BACKSPACE

    else -> Key.UNKNOWN
}

/**
 * Maps Key enum to InputBind for KeyInputEvent handling.
 */
fun Key.toInputBind(): InputBind? = when (this) {
    Key.A_KEY -> InputBind.A
    Key.B_KEY -> InputBind.B
    Key.C_KEY -> InputBind.C
    Key.D_KEY -> InputBind.D
    Key.E_KEY -> InputBind.E
    Key.F_KEY -> InputBind.F
    Key.G_KEY -> InputBind.G
    Key.H_KEY -> InputBind.H
    Key.I_KEY -> InputBind.I
    Key.J_KEY -> InputBind.J
    Key.K_KEY -> InputBind.K
    Key.L_KEY -> InputBind.L
    Key.M_KEY -> InputBind.M
    Key.N_KEY -> InputBind.N
    Key.O_KEY -> InputBind.O
    Key.P_KEY -> InputBind.P
    Key.Q_KEY -> InputBind.Q
    Key.R_KEY -> InputBind.R
    Key.S_KEY -> InputBind.S
    Key.T_KEY -> InputBind.T
    Key.U_KEY -> InputBind.U
    Key.V_KEY -> InputBind.V
    Key.W_KEY -> InputBind.W
    Key.X_KEY -> InputBind.X
    Key.Y_KEY -> InputBind.Y
    Key.Z_KEY -> InputBind.Z

    Key.LEFT -> InputBind.LEFT
    Key.RIGHT -> InputBind.RIGHT
    Key.UP -> InputBind.UP
    Key.DOWN -> InputBind.DOWN

    Key.SPACE -> InputBind.SPACE
    Key.ENTER -> InputBind.ENTER
    Key.ESCAPE -> InputBind.ESCAPE
    Key.BACKSPACE -> InputBind.BACKSPACE

    else -> null
}
