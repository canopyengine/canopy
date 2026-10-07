package io.canopy.engine.input.binds

/**
 * Canonical keyboard identities shared by raw events and action bindings. [code] is a stable Canopy identifier,
 * never an ordinal or native backend code. Backend support varies; text belongs in TextInputEvent separately.
 * [CTRL], [ALT] and [SHIFT] describe unsided modifier reports and have no physical InputBind; [UNKNOWN] is unsupported.
 */
enum class Key(
    /** Stable Canopy keyboard code, or null for unsided modifiers and unknown keys. */
    val code: Int?,
) {
    A(1),
    B(2),
    C(3),
    D(4),
    E(5),
    F(6),
    G(7),
    H(8),
    I(9),
    J(10),
    K(11),
    L(12),
    M(13),
    N(14),
    O(15),
    P(16),
    Q(17),
    R(18),
    S(19),
    T(20),
    U(21),
    V(22),
    W(23),
    X(24),
    Y(25),
    Z(26),
    NUM_0(100),
    NUM_1(101),
    NUM_2(102),
    NUM_3(103),
    NUM_4(104),
    NUM_5(105),
    NUM_6(106),
    NUM_7(107),
    NUM_8(108),
    NUM_9(109),
    LEFT(200),
    RIGHT(201),
    UP(202),
    DOWN(203),
    SPACE(300),
    ENTER(301),
    ESCAPE(302),
    TAB(303),
    BACKSPACE(304),
    INSERT(305),
    DELETE(306),
    HOME(307),
    END(308),
    PAGE_UP(309),
    PAGE_DOWN(310),
    SHIFT_LEFT(400),
    SHIFT_RIGHT(401),
    CTRL_LEFT(402),
    CTRL_RIGHT(403),
    ALT_LEFT(404),
    ALT_RIGHT(405),
    META_LEFT(406),
    META_RIGHT(407),
    CAPS_LOCK(408),
    NUM_LOCK(409),
    SCROLL_LOCK(410),
    PRINT_SCREEN(411),
    PAUSE(412),
    GRAVE(500),
    MINUS(501),
    EQUALS(502),
    LEFT_BRACKET(503),
    RIGHT_BRACKET(504),
    BACKSLASH(505),
    SEMICOLON(506),
    APOSTROPHE(507),
    COMMA(508),
    PERIOD(509),
    SLASH(510),
    F1(600),
    F2(601),
    F3(602),
    F4(603),
    F5(604),
    F6(605),
    F7(606),
    F8(607),
    F9(608),
    F10(609),
    F11(610),
    F12(611),
    NUMPAD_0(700),
    NUMPAD_1(701),
    NUMPAD_2(702),
    NUMPAD_3(703),
    NUMPAD_4(704),
    NUMPAD_5(705),
    NUMPAD_6(706),
    NUMPAD_7(707),
    NUMPAD_8(708),
    NUMPAD_9(709),
    NUMPAD_ADD(710),
    NUMPAD_SUBTRACT(711),
    NUMPAD_MULTIPLY(712),
    NUMPAD_DIVIDE(713),
    NUMPAD_DECIMAL(714),
    NUMPAD_ENTER(715),

    CTRL(null),
    ALT(null),
    SHIFT(null),
    UNKNOWN(null),
    ;

    companion object {
        /** Source migration alias for [A]; not a separate enum entry. */
        @Deprecated("Use Key.A", ReplaceWith("Key.A"))
        val A_KEY: Key get() = A

        /** Source migration alias for [B]; not a separate enum entry. */
        @Deprecated("Use Key.B", ReplaceWith("Key.B"))
        val B_KEY: Key get() = B

        /** Source migration alias for [C]; not a separate enum entry. */
        @Deprecated("Use Key.C", ReplaceWith("Key.C"))
        val C_KEY: Key get() = C

        /** Source migration alias for [D]; not a separate enum entry. */
        @Deprecated("Use Key.D", ReplaceWith("Key.D"))
        val D_KEY: Key get() = D

        /** Source migration alias for [E]; not a separate enum entry. */
        @Deprecated("Use Key.E", ReplaceWith("Key.E"))
        val E_KEY: Key get() = E

        /** Source migration alias for [F]; not a separate enum entry. */
        @Deprecated("Use Key.F", ReplaceWith("Key.F"))
        val F_KEY: Key get() = F

        /** Source migration alias for [G]; not a separate enum entry. */
        @Deprecated("Use Key.G", ReplaceWith("Key.G"))
        val G_KEY: Key get() = G

        /** Source migration alias for [H]; not a separate enum entry. */
        @Deprecated("Use Key.H", ReplaceWith("Key.H"))
        val H_KEY: Key get() = H

        /** Source migration alias for [I]; not a separate enum entry. */
        @Deprecated("Use Key.I", ReplaceWith("Key.I"))
        val I_KEY: Key get() = I

        /** Source migration alias for [J]; not a separate enum entry. */
        @Deprecated("Use Key.J", ReplaceWith("Key.J"))
        val J_KEY: Key get() = J

        /** Source migration alias for [K]; not a separate enum entry. */
        @Deprecated("Use Key.K", ReplaceWith("Key.K"))
        val K_KEY: Key get() = K

        /** Source migration alias for [L]; not a separate enum entry. */
        @Deprecated("Use Key.L", ReplaceWith("Key.L"))
        val L_KEY: Key get() = L

        /** Source migration alias for [M]; not a separate enum entry. */
        @Deprecated("Use Key.M", ReplaceWith("Key.M"))
        val M_KEY: Key get() = M

        /** Source migration alias for [N]; not a separate enum entry. */
        @Deprecated("Use Key.N", ReplaceWith("Key.N"))
        val N_KEY: Key get() = N

        /** Source migration alias for [O]; not a separate enum entry. */
        @Deprecated("Use Key.O", ReplaceWith("Key.O"))
        val O_KEY: Key get() = O

        /** Source migration alias for [P]; not a separate enum entry. */
        @Deprecated("Use Key.P", ReplaceWith("Key.P"))
        val P_KEY: Key get() = P

        /** Source migration alias for [Q]; not a separate enum entry. */
        @Deprecated("Use Key.Q", ReplaceWith("Key.Q"))
        val Q_KEY: Key get() = Q

        /** Source migration alias for [R]; not a separate enum entry. */
        @Deprecated("Use Key.R", ReplaceWith("Key.R"))
        val R_KEY: Key get() = R

        /** Source migration alias for [S]; not a separate enum entry. */
        @Deprecated("Use Key.S", ReplaceWith("Key.S"))
        val S_KEY: Key get() = S

        /** Source migration alias for [T]; not a separate enum entry. */
        @Deprecated("Use Key.T", ReplaceWith("Key.T"))
        val T_KEY: Key get() = T

        /** Source migration alias for [U]; not a separate enum entry. */
        @Deprecated("Use Key.U", ReplaceWith("Key.U"))
        val U_KEY: Key get() = U

        /** Source migration alias for [V]; not a separate enum entry. */
        @Deprecated("Use Key.V", ReplaceWith("Key.V"))
        val V_KEY: Key get() = V

        /** Source migration alias for [W]; not a separate enum entry. */
        @Deprecated("Use Key.W", ReplaceWith("Key.W"))
        val W_KEY: Key get() = W

        /** Source migration alias for [X]; not a separate enum entry. */
        @Deprecated("Use Key.X", ReplaceWith("Key.X"))
        val X_KEY: Key get() = X

        /** Source migration alias for [Y]; not a separate enum entry. */
        @Deprecated("Use Key.Y", ReplaceWith("Key.Y"))
        val Y_KEY: Key get() = Y

        /** Source migration alias for [Z]; not a separate enum entry. */
        @Deprecated("Use Key.Z", ReplaceWith("Key.Z"))
        val Z_KEY: Key get() = Z
    }
}
