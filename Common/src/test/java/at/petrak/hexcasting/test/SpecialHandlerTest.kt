package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.casting.actions.math.SpecialHandlerNumberLiteral
import at.petrak.hexcasting.common.casting.actions.stack.SpecialHandlerMask
import at.petrak.hexcasting.common.lib.hex.HexSpecialHandlers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Special handlers run BEFORE registered actions during pattern resolution — they recognize
 * number-literal and mask patterns. A regression in pattern recognition silently drops entire
 * classes of spells. {@code tryMatch} is a pure function of the pattern, but Kotlin enforces the
 * non-null env param at method entry. We pass a [StubCastingEnv] whose methods all throw — the
 * factories never dereference env, so the throws never fire.
 */
class SpecialHandlerTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = TestBootstrap.init()
    }

    private val env = StubCastingEnv()

    @Test
    fun numberLiteralFactoryRecognizesOne() {
        val result = SpecialHandlerNumberLiteral.Factory().tryMatch(
            HexPattern.fromAngles("aqaaw", HexDir.EAST), env
        )
        assertNotNull(result, "aqaaw should parse as number literal")
        assertEquals(1.0, result!!.x, 0.0, "aqaaw = 1")
    }

    @Test
    fun numberLiteralNegativeBranch() {
        val result = SpecialHandlerNumberLiteral.Factory().tryMatch(
            HexPattern.fromAngles("deddw", HexDir.EAST), env
        )
        assertNotNull(result, "deddw should parse as number literal")
        assertEquals(-1.0, result!!.x, 0.0, "deddw = -1")
    }

    @Test
    fun numberLiteralFactoryRejectsOther() {
        // Patterns not starting with aqaa or dedd must return null — otherwise every pattern in
        // the game would accidentally register as a number literal.
        val result = SpecialHandlerNumberLiteral.Factory().tryMatch(
            HexPattern.fromAngles("qaq", HexDir.NORTH_EAST), env
        )
        assertNull(result, "qaq is not a number literal")
    }

    @Test
    fun numberLiteralComputesCompositeLiteral() {
        // "aqaa" + "wq" → tail 'w' (+1) then 'q' (+5) = 6.
        val result = SpecialHandlerNumberLiteral.Factory().tryMatch(
            HexPattern.fromAngles("aqaawq", HexDir.EAST), env
        )
        assertNotNull(result)
        assertEquals(6.0, result!!.x, 0.0, "wq after aqaa = 1 + 5 = 6")
    }

    @Test
    fun numberLiteralDoublingAndHalving() {
        // 'a' doubles, 'd' halves. Tail "wa" = +1 then ×2 = 2.
        val doubled = SpecialHandlerNumberLiteral.Factory().tryMatch(
            HexPattern.fromAngles("aqaawa", HexDir.EAST), env
        )
        assertNotNull(doubled)
        assertEquals(2.0, doubled!!.x, 0.0, "wa after aqaa: (0 + 1) * 2 = 2")

        // "wd" = +1 then /2 = 0.5
        val halved = SpecialHandlerNumberLiteral.Factory().tryMatch(
            HexPattern.fromAngles("aqaawd", HexDir.EAST), env
        )
        assertNotNull(halved)
        assertEquals(0.5, halved!!.x, 0.0, "wd after aqaa: (0 + 1) / 2 = 0.5")
    }

    @Test
    fun specialHandlerFactoriesAreInRegistry() {
        // Port risk: the Factory instances must be non-null and matching what HexSpecialHandlers
        // exposes. A stale reference would cause cross-wiring breaks.
        assertNotNull(HexSpecialHandlers.NUMBER)
        assertNotNull(HexSpecialHandlers.MASK)
        assertTrue(HexSpecialHandlers.NUMBER is SpecialHandlerNumberLiteral.Factory)
        assertTrue(HexSpecialHandlers.MASK is SpecialHandlerMask.Factory)
    }
}
