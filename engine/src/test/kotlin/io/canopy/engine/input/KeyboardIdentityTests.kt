package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.binds.toInputBind
import io.canopy.engine.input.binds.toKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KeyboardIdentityTests {
    private val legacy = requireNotNull(javaClass.getResource("/input-bind-legacy.tsv"))
        .readText().lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()

    @Test
    fun `every saved binding retains its name code category order and JSON representation`() {
        // Arrange: fixture captured from main before this refactor, including all mouse bindings.
        val savedNames = legacy.joinToString(",", prefix = "[", postfix = "]") { "\"${it[0]}\"" }
        val saved = """{"mappings":[{"name":"legacy","binds":$savedNames}]}"""
        // Act
        val decoded = Json.decodeFromString<InputData>(saved)
        val mapper = InputMapper()
        mapper.loadData(decoded)
        // Assert
        assertEquals(legacy.map { it[0] }, InputBind.entries.map { it.name })
        legacy.zip(InputBind.entries).forEach { (expected, bind) ->
            assertEquals(expected[1], bind.type.name, bind.name)
            assertEquals(expected[2].toInt(), bind.code, bind.name)
            assertEquals(bind, InputBind.from(expected[0].lowercase()))
        }
        assertEquals(InputBind.entries, mapper.actions.getValue("legacy"))
        assertEquals(Json.parseToJsonElement(saved), Json.parseToJsonElement(Json.encodeToString(mapper.toData())))
    }

    @Test
    fun `all physical keyboard identities round trip without collisions or unsupported fallbacks`() {
        val keyboard = InputBind.entries.filter { it.type == InputBind.Type.Keyboard }
        assertEquals(103, keyboard.size)
        assertEquals(keyboard.size, keyboard.map { it.toKey() }.toSet().size)
        keyboard.forEach { bind ->
            assertTrue(bind.toKey() != Key.UNKNOWN, bind.name)
            assertSame(bind.key, bind.toKey())
            assertEquals(bind.code, bind.toKey().code)
            assertSame(bind, bind.toKey().toInputBind(), bind.name)
        }
        InputBind.entries.filter { it.type == InputBind.Type.Mouse }.forEach {
            assertNull(it.key)
            assertSame(Key.UNKNOWN, it.toKey())
        }
        listOf(Key.CTRL, Key.ALT, Key.SHIFT, Key.UNKNOWN).forEach {
            assertNull(it.code)
            assertNull(it.toInputBind())
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy letter aliases are canonical identities rather than additional enum values`() {
        val aliases = listOf(
            Key.A_KEY to Key.A,
            Key.B_KEY to Key.B,
            Key.C_KEY to Key.C,
            Key.D_KEY to Key.D,
            Key.E_KEY to Key.E,
            Key.F_KEY to Key.F,
            Key.G_KEY to Key.G,
            Key.H_KEY to Key.H,
            Key.I_KEY to Key.I,
            Key.J_KEY to Key.J,
            Key.K_KEY to Key.K,
            Key.L_KEY to Key.L,
            Key.M_KEY to Key.M,
            Key.N_KEY to Key.N,
            Key.O_KEY to Key.O,
            Key.P_KEY to Key.P,
            Key.Q_KEY to Key.Q,
            Key.R_KEY to Key.R,
            Key.S_KEY to Key.S,
            Key.T_KEY to Key.T,
            Key.U_KEY to Key.U,
            Key.V_KEY to Key.V,
            Key.W_KEY to Key.W,
            Key.X_KEY to Key.X,
            Key.Y_KEY to Key.Y,
            Key.Z_KEY to Key.Z
        )
        aliases.forEach { (alias, canonical) -> assertSame(canonical, alias) }
        assertEquals(aliases.size, aliases.map { it.first }.toSet().size)
        assertTrue(Key.entries.none { it.name.endsWith("_KEY") })
    }
}
