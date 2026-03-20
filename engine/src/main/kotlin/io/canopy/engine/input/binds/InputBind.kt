package io.canopy.engine.input.binds

import kotlinx.serialization.Serializable

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

    @Serializable
    enum class Type {
        Keyboard,
        Mouse,
    }
}
