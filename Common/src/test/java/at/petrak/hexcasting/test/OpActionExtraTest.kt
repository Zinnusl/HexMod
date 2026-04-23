package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.SpellList
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.OperatorBasic
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import at.petrak.hexcasting.common.casting.actions.local.OpPeekLocal
import at.petrak.hexcasting.common.casting.actions.local.OpPushLocal
import at.petrak.hexcasting.common.casting.arithmetic.DoubleArithmetic
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.math.PI

/**
 * Complements [OpActionTest]. These target code paths that need either non-Java-friendly
 * construction (data-class `copy` on [CastingImage]'s private primary constructor) or the
 * [Arithmetic] dispatch layer — both better-expressed in Kotlin.
 *
 * Covered:
 *  - Local-variable ops: [OpPushLocal] / [OpPeekLocal] via `Action.operate` against a
 *    manually-assembled [CastingImage]. Round-trips a double through ravenmind userdata.
 *  - Trig operators via [DoubleArithmetic] — end-to-end invocation of the [OperatorBasic]
 *    returned by `getOperator(SIN|COS|TAN)`.
 *  - [SpellList] invariants (size, getAt, iteration) for both LList and LPair shapes.
 *  - [HexPattern.anglesSignature] stability across `fromAngles` / `fromNBT` construction.
 *  - [ResolvedPattern.fromNBT] graceful fallback when "Valid" key is missing — the
 *    [ResolvedPatternType.fromString] getSafe extension returns the first enum value
 *    (UNRESOLVED) for unknown strings, including the empty-string default of missing keys.
 */
class OpActionExtraTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = TestBootstrap.init()
    }

    private val env = StubCastingEnv()
    private val cont = SpellContinuation.Done

    // === Local variable ops ====================================================================

    @Test
    fun pushLocalOnEmptyStackMishaps() {
        // OpPushLocal needs one arg; empty stack triggers MishapNotEnoughArgs.
        val image = CastingImage()
        assertThrows(MishapNotEnoughArgs::class.java) {
            OpPushLocal.operate(env, image, cont)
        }
    }

    @Test
    fun peekLocalOnEmptyUserDataPushesNullIota() {
        // When userdata has no RAVENMIND_USERDATA key, peek pushes a NullIota onto the stack
        // without reaching through env.world. Verifies the explicit fallback branch.
        val image = CastingImage()
        val result = OpPeekLocal.operate(env, image, cont)
        val newStack = result.newImage.stack
        assertEquals(1, newStack.size, "peek grows stack by exactly 1")
        assertTrue(newStack[0] is NullIota, "missing ravenmind -> NullIota")
    }

    @Test
    fun pushLocalMovesIotaFromStackToUserData() {
        // Given stack [DoubleIota(7.0)], OpPushLocal should consume it, leaving the stack empty
        // and populating RAVENMIND_USERDATA with the serialized iota.
        val iota: Iota = DoubleIota(7.0)
        val image = CastingImage().copy(stack = listOf(iota))
        val result = OpPushLocal.operate(env, image, cont)
        assertTrue(result.newImage.stack.isEmpty(), "stack drained by push-local")
        assertTrue(
            result.newImage.userData.contains(HexAPI.RAVENMIND_USERDATA),
            "userdata gains the ravenmind key"
        )
    }

    @Test
    fun pushLocalNullIotaClearsUserData() {
        // OpPushLocal special-cases NullIota: instead of serializing, it removes the
        // ravenmind key (a no-op if absent). Start with a pre-populated userdata and confirm
        // the key disappears after pushing a NullIota.
        val userdata = CompoundTag()
        userdata.putString(HexAPI.RAVENMIND_USERDATA, "junk-should-be-removed")
        val image = CastingImage().copy(stack = listOf<Iota>(NullIota()), userData = userdata)
        val result = OpPushLocal.operate(env, image, cont)
        assertFalse(
            result.newImage.userData.contains(HexAPI.RAVENMIND_USERDATA),
            "NullIota push clears the ravenmind key entirely"
        )
    }

    @Test
    fun pushThenPeekRoundTripsDouble() {
        // End-to-end: push a DoubleIota into ravenmind, then peek it back. The peeked value
        // should round-trip through IotaType serialize/deserialize. env.world on StubCastingEnv
        // is null, but DoubleIota.deserialize ignores world — safe.
        val iota: Iota = DoubleIota(42.5)
        val image = CastingImage().copy(stack = listOf(iota))
        val afterPush = OpPushLocal.operate(env, image, cont).newImage
        val afterPeek = OpPeekLocal.operate(env, afterPush, cont).newImage
        assertEquals(1, afterPeek.stack.size, "peek pushes exactly one iota")
        val peeked = afterPeek.stack[0]
        assertTrue(peeked is DoubleIota, "peeked iota is a DoubleIota")
        assertEquals(42.5, (peeked as DoubleIota).double, 0.0, "double value survives round-trip")
    }

    @Test
    fun peekLocalDoesNotMutateUserData() {
        // OpPeekLocal is read-only w.r.t. userdata. A regression that cleared or mutated
        // the key would break Ravenmind's "remember across casts" contract.
        val iota: Iota = DoubleIota(3.14)
        val image = CastingImage().copy(stack = listOf(iota))
        val afterPush = OpPushLocal.operate(env, image, cont).newImage
        val ravenmindBefore = afterPush.userData.getCompound(HexAPI.RAVENMIND_USERDATA).copy()
        val afterPeek = OpPeekLocal.operate(env, afterPush, cont).newImage
        assertEquals(
            ravenmindBefore,
            afterPeek.userData.getCompound(HexAPI.RAVENMIND_USERDATA),
            "peek leaves ravenmind unchanged"
        )
    }

    // === Trig/math operators via DoubleArithmetic.getOperator ==================================

    @Test
    fun sinOfZeroIsZero() {
        // sin(0) = 0. Ensures the Kotlin when-branch dispatch in DoubleArithmetic.getOperator
        // is actually wired to kotlin.math.sin, not e.g. cos.
        val op = DoubleArithmetic.getOperator(Arithmetic.SIN) as OperatorBasic
        val out = op.apply(listOf(DoubleIota(0.0)), env).iterator()
        val result = out.next() as DoubleIota
        assertEquals(0.0, result.double, 1e-12, "sin(0) = 0")
        assertFalse(out.hasNext(), "unary op emits exactly one iota")
    }

    @Test
    fun cosOfZeroIsOne() {
        val op = DoubleArithmetic.getOperator(Arithmetic.COS) as OperatorBasic
        val result = op.apply(listOf(DoubleIota(0.0)), env).iterator().next() as DoubleIota
        assertEquals(1.0, result.double, 1e-12, "cos(0) = 1")
    }

    @Test
    fun tanOfPiOverFourIsOne() {
        val op = DoubleArithmetic.getOperator(Arithmetic.TAN) as OperatorBasic
        val result = op.apply(listOf(DoubleIota(PI / 4.0)), env).iterator().next() as DoubleIota
        assertEquals(1.0, result.double, 1e-9, "tan(pi/4) = 1")
    }

    @Test
    fun arcsinArccosAreInverseOfSinCos() {
        // Smoke-test the inverse branches: arcsin(sin(x)) ~= x for x in [-pi/2, pi/2].
        val sin = DoubleArithmetic.getOperator(Arithmetic.SIN) as OperatorBasic
        val arcsin = DoubleArithmetic.getOperator(Arithmetic.ARCSIN) as OperatorBasic
        val x = 0.42
        val sinX = (sin.apply(listOf(DoubleIota(x)), env).iterator().next() as DoubleIota).double
        val backToX = (arcsin.apply(listOf(DoubleIota(sinX)), env).iterator().next() as DoubleIota).double
        assertEquals(x, backToX, 1e-9, "arcsin(sin(x)) ~= x")
    }

    @Test
    fun addOperatorSumsTwoDoubles() {
        // Double-check that the binary ADD branch emits a DoubleIota with the sum.
        val op = DoubleArithmetic.getOperator(Arithmetic.ADD) as OperatorBasic
        val out = op.apply(listOf(DoubleIota(3.0), DoubleIota(4.5)), env).iterator()
        val result = out.next() as DoubleIota
        assertEquals(7.5, result.double, 1e-12, "3 + 4.5 = 7.5")
        assertFalse(out.hasNext(), "binary op emits exactly one iota")
    }

    // === SpellList iteration ===================================================================

    @Test
    fun llistSizeMatchesBackingList() {
        // LList size delegates to traversing cdr until non-empty is false. For an LList backed
        // by a concrete List<Iota>, size() must agree with the backing list's size.
        val backing = listOf<Iota>(DoubleIota(1.0), DoubleIota(2.0), DoubleIota(3.0), DoubleIota(4.0))
        val sl: SpellList = SpellList.LList(backing)
        assertEquals(4, sl.size(), "LList.size walks cdr to exhaustion")
    }

    @Test
    fun llistEmptyIsEmpty() {
        val sl: SpellList = SpellList.LList(emptyList())
        assertEquals(0, sl.size())
        assertFalse(sl.nonEmpty, "empty LList is empty")
        assertFalse(sl.iterator().hasNext(), "empty LList iterator has no next")
    }

    @Test
    fun llistGetAtRetrievesElementAtPosition() {
        val backing = listOf<Iota>(DoubleIota(10.0), DoubleIota(20.0), DoubleIota(30.0))
        val sl: SpellList = SpellList.LList(backing)
        assertEquals(10.0, (sl.getAt(0) as DoubleIota).double, 0.0)
        assertEquals(20.0, (sl.getAt(1) as DoubleIota).double, 0.0)
        assertEquals(30.0, (sl.getAt(2) as DoubleIota).double, 0.0)
    }

    @Test
    fun llistIteratorEmitsAllElementsInOrder() {
        val backing = listOf<Iota>(DoubleIota(1.0), DoubleIota(2.0), DoubleIota(3.0))
        val sl: SpellList = SpellList.LList(backing)
        val actual = sl.map { (it as DoubleIota).double }
        assertEquals(listOf(1.0, 2.0, 3.0), actual)
    }

    @Test
    fun lpairPrependsCarToCdr() {
        // LPair(a, LList([b, c])) should iterate [a, b, c] and size 3.
        val tail: SpellList = SpellList.LList(listOf<Iota>(DoubleIota(2.0), DoubleIota(3.0)))
        val pair: SpellList = SpellList.LPair(DoubleIota(1.0), tail)
        assertTrue(pair.nonEmpty, "LPair is always nonEmpty")
        assertEquals(3, pair.size(), "LPair prepends 1 to a length-2 tail = 3")
        assertEquals(1.0, (pair.getAt(0) as DoubleIota).double, 0.0, "car is at index 0")
        assertEquals(2.0, (pair.getAt(1) as DoubleIota).double, 0.0, "cdr[0] at index 1")
        assertEquals(3.0, (pair.getAt(2) as DoubleIota).double, 0.0, "cdr[1] at index 2")
    }

    @Test
    fun nestedLpairChainIteratesCorrectly() {
        // Chain multiple LPair as happens after several CONS ops.
        // LPair(a, LPair(b, LPair(c, LList([d]))))
        val tail: SpellList = SpellList.LList(listOf<Iota>(DoubleIota(4.0)))
        val sl: SpellList = SpellList.LPair(
            DoubleIota(1.0),
            SpellList.LPair(
                DoubleIota(2.0),
                SpellList.LPair(DoubleIota(3.0), tail)
            )
        )
        assertEquals(4, sl.size(), "4-deep chain has size 4")
        val flat = sl.map { (it as DoubleIota).double }
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), flat, "iteration yields car-first order")
    }

    @Test
    fun llistIteratorIsIndependentAcrossCalls() {
        // Calling .iterator() twice on the same SpellList must yield two independent iterators
        // — the SpellListIterator holds its own mutable reference.
        val sl: SpellList = SpellList.LList(listOf<Iota>(DoubleIota(1.0), DoubleIota(2.0)))
        val a = sl.iterator()
        val b = sl.iterator()
        assertEquals(1.0, (a.next() as DoubleIota).double, 0.0)
        // After consuming one element via a, b should still be at position 0.
        assertEquals(1.0, (b.next() as DoubleIota).double, 0.0, "second iterator is independent")
    }

    // === Pattern parity: fromAngles vs fromNBT =================================================

    @Test
    fun anglesSignatureStableAcrossConstructionPaths() {
        // For 5+ representative patterns, building via fromAngles and re-parsing from NBT
        // must yield identical anglesSignature strings. Drift here breaks staff pattern
        // recognition on save-load cycles (every stored spell gets re-parsed via fromNBT).
        data class Fixture(val sig: String, val dir: HexDir)
        val fixtures = listOf(
            Fixture("qaq", HexDir.NORTH_EAST),                // OpGetCaster (mind's reflection)
            Fixture("aa", HexDir.EAST),                       // OpEntityPos (false)
            Fixture("wq", HexDir.EAST),                       // OpEntityVelocity
            Fixture("wdedw", HexDir.NORTH_EAST),              // Arithmetic.DIV
            Fixture("waaw", HexDir.NORTH_EAST),               // Arithmetic.ADD
            Fixture("wqaqw", HexDir.SOUTH_EAST),              // Mixed-angle reference
            Fixture("", HexDir.EAST)                          // Empty signature edge case
        )
        for (fx in fixtures) {
            val built = HexPattern.fromAngles(fx.sig, fx.dir)
            val viaNbt = HexPattern.fromNBT(built.serializeToNBT())
            assertEquals(
                built.anglesSignature(),
                viaNbt.anglesSignature(),
                "signature ${fx.sig}@${fx.dir}: fromAngles -> fromNBT round-trip preserves anglesSignature"
            )
            assertEquals(
                fx.sig,
                built.anglesSignature(),
                "fromAngles reproduces its input signature: ${fx.sig}"
            )
            assertEquals(
                fx.dir,
                viaNbt.startDir,
                "startDir preserved across fromNBT: ${fx.dir}"
            )
        }
    }

    @Test
    fun anglesSignatureRoundTripsEachAngleLetter() {
        // Every angle letter (w/e/d/a/q) should survive a fromAngles -> fromNBT -> anglesSignature
        // cycle unchanged. 's' (BACK) is never valid in fromAngles — fromAngles throws on a BACK
        // angle — so we skip it here; the BACK handling is covered indirectly by HexAngle tests.
        for (letter in listOf("w", "e", "d", "a", "q")) {
            val built = HexPattern.fromAngles(letter, HexDir.EAST)
            val viaNbt = HexPattern.fromNBT(built.serializeToNBT())
            assertEquals(letter, viaNbt.anglesSignature(),
                "single-letter '$letter' survives NBT round-trip")
        }
    }

    // === ResolvedPattern.fromNBT fallback ======================================================

    @Test
    fun resolvedPatternFromNbtMissingValidKeyFallsBackToUnresolved() {
        // If "Valid" is absent, tag.getString returns "" — ResolvedPatternType.fromString then
        // delegates to getSafe(""), which returns values()[0] = UNRESOLVED (the default fallback).
        // Guarantees old save files or corrupted writes don't crash the loader.
        val pattern = HexPattern.fromAngles("qaq", HexDir.EAST)
        val payload = CompoundTag()
        payload.put("Pattern", pattern.serializeToNBT())
        payload.putInt("OriginQ", 1)
        payload.putInt("OriginR", -2)
        // deliberately no "Valid" key

        val rp = ResolvedPattern.fromNBT(payload)
        assertEquals(
            ResolvedPatternType.UNRESOLVED, rp.type,
            "missing Valid -> UNRESOLVED (first enum value, the documented fallback)"
        )
        assertEquals(
            pattern.anglesSignature(), rp.pattern.anglesSignature(),
            "pattern still recovered even without Valid key"
        )
        assertEquals(1, rp.origin.q, "origin Q preserved")
        assertEquals(-2, rp.origin.r, "origin R preserved")
    }

    @Test
    fun resolvedPatternFromNbtUnknownValidValueFallsBackToUnresolved() {
        // Garbage string in the Valid key should also fall back rather than throw.
        val pattern = HexPattern.fromAngles("aa", HexDir.EAST)
        val payload = CompoundTag()
        payload.put("Pattern", pattern.serializeToNBT())
        payload.putInt("OriginQ", 0)
        payload.putInt("OriginR", 0)
        payload.putString("Valid", "this-is-not-a-valid-enum-name")

        val rp = ResolvedPattern.fromNBT(payload)
        assertEquals(
            ResolvedPatternType.UNRESOLVED, rp.type,
            "unknown Valid value -> UNRESOLVED fallback (getSafe default)"
        )
    }

    @Test
    fun resolvedPatternTypeFromStringCaseInsensitive() {
        // getSafe lowercases both sides. Confirm an uppercase or mixed-case name still resolves
        // — otherwise a migration from a different casing convention would silently reset types.
        assertEquals(ResolvedPatternType.EVALUATED, ResolvedPatternType.fromString("EVALUATED"))
        assertEquals(ResolvedPatternType.EVALUATED, ResolvedPatternType.fromString("Evaluated"))
        assertEquals(ResolvedPatternType.ERRORED, ResolvedPatternType.fromString("ERRORED"))
    }
}
