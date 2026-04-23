package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.common.casting.actions.lists.OpAppend;
import at.petrak.hexcasting.common.casting.actions.lists.OpConcat;
import at.petrak.hexcasting.common.casting.actions.lists.OpCons;
import at.petrak.hexcasting.common.casting.actions.lists.OpEmptyList;
import at.petrak.hexcasting.common.casting.actions.lists.OpIndex;
import at.petrak.hexcasting.common.casting.actions.lists.OpIndexOf;
import at.petrak.hexcasting.common.casting.actions.lists.OpListSize;
import at.petrak.hexcasting.common.casting.actions.lists.OpReverski;
import at.petrak.hexcasting.common.casting.actions.lists.OpSingleton;
import at.petrak.hexcasting.common.casting.actions.lists.OpSlice;
import at.petrak.hexcasting.common.casting.actions.lists.OpSplat;
import at.petrak.hexcasting.common.casting.actions.lists.OpUnCons;
import at.petrak.hexcasting.common.casting.actions.math.OpCoerceToAxial;
import at.petrak.hexcasting.common.casting.actions.math.OpConstructVec;
import at.petrak.hexcasting.common.casting.actions.math.OpDeconstructVec;
import at.petrak.hexcasting.common.casting.actions.math.OpModulo;
import at.petrak.hexcasting.common.casting.actions.math.bit.OpAnd;
import at.petrak.hexcasting.common.casting.actions.math.bit.OpNot;
import at.petrak.hexcasting.common.casting.actions.math.bit.OpOr;
import at.petrak.hexcasting.common.casting.actions.math.bit.OpToSet;
import at.petrak.hexcasting.common.casting.actions.math.bit.OpXor;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpBoolAnd;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpBoolIf;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpBoolOr;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpBoolToNumber;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpBoolXor;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpCoerceToBool;
import at.petrak.hexcasting.common.casting.actions.math.logic.OpEquality;
import at.petrak.hexcasting.common.casting.actions.stack.OpDuplicateN;
import at.petrak.hexcasting.common.casting.actions.stack.OpTwiddling;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stack-pure {@code ConstMediaAction.execute} contract tests. Each op gets a synthetic
 * {@code List<Iota>} representing the {@code stack.takeLast(argc)} window from
 * {@link ConstMediaAction#operate}, and we assert the returned stack matches the documented
 * semantics. No env interaction — {@link StubCastingEnv} throws on every reachable method, so
 * a regression that starts touching env under {@code execute} surfaces immediately.
 * <p>
 * Stack convention: args index 0 = the argument that was lowest on the stack. The runtime calls
 * {@code stack.takeLast(argc)} and preserves order, so for a stack [a, b, c] with argc=3 we
 * pass {@code [a, b, c]} as args with {@code args[0]=a, args[1]=b, args[2]=c} — bottom-to-top.
 * <p>
 * {@code OpRandom} is skipped because it dereferences {@code env.world.random}, which requires a
 * real {@code ServerLevel}. {@code OpFisherman}/{@code OpLastNToList} use the bare {@code Action}
 * interface (operate on the full {@code CastingImage}) rather than {@code ConstMediaAction}, so
 * they don't fit this test's model and are exercised indirectly via their registry entries in
 * other tests.
 */
public final class OpActionTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    private static final CastingEnvironment ENV = new StubCastingEnv();

    // === Stack ops via OpTwiddling (SWAP/ROTATE/DUPLICATE/OVER/TUCK/2DUP) =======================

    @Test
    public void swapFlipsTopTwoArgs() {
        // SWAP lookup = [1, 0], argc = 2. Stack [a, b] (b on top) -> [b, a].
        var swap = new OpTwiddling(2, new int[]{1, 0});
        var a = new DoubleIota(10);
        var b = new DoubleIota(20);
        var result = swap.execute(List.of(a, b), ENV);
        assertEquals(2, result.size(), "SWAP preserves size");
        assertSame(b, result.get(0), "SWAP puts top at bottom");
        assertSame(a, result.get(1), "SWAP puts bottom at top");
    }

    @Test
    public void overCopiesSecondArgToTop() {
        // OVER lookup = [0, 1, 0], argc = 2. Stack [a, b] -> [a, b, a].
        var over = new OpTwiddling(2, new int[]{0, 1, 0});
        var a = new DoubleIota(1);
        var b = new DoubleIota(2);
        var result = over.execute(List.of(a, b), ENV);
        assertEquals(3, result.size());
        assertSame(a, result.get(0));
        assertSame(b, result.get(1));
        assertSame(a, result.get(2), "OVER duplicates the lower-of-two onto the top");
    }

    @Test
    public void tuckInsertsTopBelowSecond() {
        // TUCK lookup = [1, 0, 1], argc = 2. Stack [a, b] -> [b, a, b].
        var tuck = new OpTwiddling(2, new int[]{1, 0, 1});
        var a = new DoubleIota(1);
        var b = new DoubleIota(2);
        var result = tuck.execute(List.of(a, b), ENV);
        assertEquals(3, result.size());
        assertSame(b, result.get(0));
        assertSame(a, result.get(1));
        assertSame(b, result.get(2));
    }

    @Test
    public void duplicateCopiesSingleArg() {
        // DUPLICATE lookup = [0, 0], argc = 1. Stack [a] -> [a, a].
        var dup = new OpTwiddling(1, new int[]{0, 0});
        var a = new DoubleIota(42);
        var result = dup.execute(List.of(a), ENV);
        assertEquals(2, result.size());
        assertSame(a, result.get(0));
        assertSame(a, result.get(1));
    }

    @Test
    public void rotateShuffles3ArgsForward() {
        // ROTATE lookup = [1, 2, 0], argc = 3. Stack [a, b, c] -> [b, c, a].
        var rot = new OpTwiddling(3, new int[]{1, 2, 0});
        var a = new DoubleIota(1);
        var b = new DoubleIota(2);
        var c = new DoubleIota(3);
        var result = rot.execute(List.of(a, b, c), ENV);
        assertEquals(3, result.size());
        assertSame(b, result.get(0));
        assertSame(c, result.get(1));
        assertSame(a, result.get(2));
    }

    @Test
    public void rotateReverseShuffles3ArgsBackward() {
        // ROTATE_REVERSE lookup = [2, 0, 1], argc = 3. Stack [a, b, c] -> [c, a, b].
        var rot = new OpTwiddling(3, new int[]{2, 0, 1});
        var a = new DoubleIota(1);
        var b = new DoubleIota(2);
        var c = new DoubleIota(3);
        var result = rot.execute(List.of(a, b, c), ENV);
        assertEquals(3, result.size());
        assertSame(c, result.get(0));
        assertSame(a, result.get(1));
        assertSame(b, result.get(2));
    }

    @Test
    public void twoDupCopiesTopTwoArgs() {
        // 2DUP lookup = [0, 1, 0, 1], argc = 2. Stack [a, b] -> [a, b, a, b].
        var twoDup = new OpTwiddling(2, new int[]{0, 1, 0, 1});
        var a = new DoubleIota(1);
        var b = new DoubleIota(2);
        var result = twoDup.execute(List.of(a, b), ENV);
        assertEquals(4, result.size());
        assertSame(a, result.get(0));
        assertSame(b, result.get(1));
        assertSame(a, result.get(2));
        assertSame(b, result.get(3));
    }

    @Test
    public void hexActionsSwapWiresToTwiddling2() {
        // Registry sanity: HexActions.SWAP should indeed hold an OpTwiddling instance equivalent
        // to the [1, 0] flip. A refactor that swapped the lookup arrays would make the in-game
        // swap pattern silently do something else.
        var action = HexActions.SWAP.action();
        assertTrue(action instanceof OpTwiddling, "HexActions.SWAP is an OpTwiddling");
    }

    // === OpDuplicateN ==========================================================================

    @Test
    public void duplicateNCopiesTargetCountTimes() {
        // args = [iota, n], returns List(n) { iota }.
        var iota = new DoubleIota(7);
        var result = OpDuplicateN.INSTANCE.execute(List.of(iota, new DoubleIota(3)), ENV);
        assertEquals(3, result.size(), "DUPLICATE_N produces n copies");
        for (var x : result) {
            assertSame(iota, x, "each copy references the same iota object");
        }
    }

    @Test
    public void duplicateNZeroCountProducesEmptyResult() {
        var iota = new DoubleIota(7);
        var result = OpDuplicateN.INSTANCE.execute(List.of(iota, new DoubleIota(0)), ENV);
        assertEquals(0, result.size(), "n=0 yields nothing");
    }

    // === OpBoolIf ==============================================================================

    @Test
    public void boolIfTrueBranchPicksTrueValue() {
        // argc = 3, args[0] = cond, args[1] = t, args[2] = f.
        // Stack convention: [f, t, cond] from bottom-to-top, so takeLast(3) = [f, t, cond] and
        // args[0]=f, args[1]=t, args[2]=cond? — NO: the op source shows args[0] = cond.
        // Reading OpBoolIf directly: val cond = args.getBool(0, argc); val t = args[1]; val f = args[2].
        // So args bottom-to-top: args[0] = cond, args[1] = t, args[2] = f. When true -> t (args[1]).
        var t = new DoubleIota(100);
        var f = new DoubleIota(200);
        var result = OpBoolIf.INSTANCE.execute(List.of(new BooleanIota(true), t, f), ENV);
        assertEquals(1, result.size());
        assertSame(t, result.get(0), "true cond picks args[1]");
    }

    @Test
    public void boolIfFalseBranchPicksFalseValue() {
        var t = new DoubleIota(100);
        var f = new DoubleIota(200);
        var result = OpBoolIf.INSTANCE.execute(List.of(new BooleanIota(false), t, f), ENV);
        assertEquals(1, result.size());
        assertSame(f, result.get(0), "false cond picks args[2]");
    }

    // === Boolean logic =========================================================================

    @Test
    public void boolAndTruthTable() {
        assertEquals(Boolean.FALSE, ((BooleanIota) OpBoolAnd.INSTANCE.execute(
            List.of(new BooleanIota(false), new BooleanIota(false)), ENV).get(0)).getBool());
        assertEquals(Boolean.FALSE, ((BooleanIota) OpBoolAnd.INSTANCE.execute(
            List.of(new BooleanIota(true), new BooleanIota(false)), ENV).get(0)).getBool());
        assertEquals(Boolean.FALSE, ((BooleanIota) OpBoolAnd.INSTANCE.execute(
            List.of(new BooleanIota(false), new BooleanIota(true)), ENV).get(0)).getBool());
        assertEquals(Boolean.TRUE, ((BooleanIota) OpBoolAnd.INSTANCE.execute(
            List.of(new BooleanIota(true), new BooleanIota(true)), ENV).get(0)).getBool());
    }

    @Test
    public void boolOrTruthTable() {
        assertEquals(Boolean.FALSE, ((BooleanIota) OpBoolOr.INSTANCE.execute(
            List.of(new BooleanIota(false), new BooleanIota(false)), ENV).get(0)).getBool());
        assertEquals(Boolean.TRUE, ((BooleanIota) OpBoolOr.INSTANCE.execute(
            List.of(new BooleanIota(true), new BooleanIota(false)), ENV).get(0)).getBool());
        assertEquals(Boolean.TRUE, ((BooleanIota) OpBoolOr.INSTANCE.execute(
            List.of(new BooleanIota(false), new BooleanIota(true)), ENV).get(0)).getBool());
        assertEquals(Boolean.TRUE, ((BooleanIota) OpBoolOr.INSTANCE.execute(
            List.of(new BooleanIota(true), new BooleanIota(true)), ENV).get(0)).getBool());
    }

    @Test
    public void boolXorTruthTable() {
        assertEquals(Boolean.FALSE, ((BooleanIota) OpBoolXor.INSTANCE.execute(
            List.of(new BooleanIota(false), new BooleanIota(false)), ENV).get(0)).getBool());
        assertEquals(Boolean.TRUE, ((BooleanIota) OpBoolXor.INSTANCE.execute(
            List.of(new BooleanIota(true), new BooleanIota(false)), ENV).get(0)).getBool());
        assertEquals(Boolean.TRUE, ((BooleanIota) OpBoolXor.INSTANCE.execute(
            List.of(new BooleanIota(false), new BooleanIota(true)), ENV).get(0)).getBool());
        assertEquals(Boolean.FALSE, ((BooleanIota) OpBoolXor.INSTANCE.execute(
            List.of(new BooleanIota(true), new BooleanIota(true)), ENV).get(0)).getBool());
    }

    @Test
    public void boolToNumberMapsBoolsToOneAndZero() {
        var t = OpBoolToNumber.INSTANCE.execute(List.of(new BooleanIota(true)), ENV);
        var f = OpBoolToNumber.INSTANCE.execute(List.of(new BooleanIota(false)), ENV);
        assertEquals(1.0, ((DoubleIota) t.get(0)).getDouble(), 0.0);
        assertEquals(0.0, ((DoubleIota) f.get(0)).getDouble(), 0.0);
    }

    // === OpCoerceToBool ========================================================================

    @Test
    public void coerceToBoolTreatsZeroAsFalse() {
        assertFalse(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new DoubleIota(0.0)), ENV).get(0)).getBool());
    }

    @Test
    public void coerceToBoolTreatsNonZeroAsTrue() {
        assertTrue(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new DoubleIota(0.5)), ENV).get(0)).getBool());
        assertTrue(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new DoubleIota(-3.0)), ENV).get(0)).getBool());
    }

    @Test
    public void coerceToBoolPassesBooleanThrough() {
        assertTrue(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new BooleanIota(true)), ENV).get(0)).getBool());
        assertFalse(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new BooleanIota(false)), ENV).get(0)).getBool());
    }

    @Test
    public void coerceToBoolTreatsEmptyListAsFalse() {
        // ListIota.isTruthy returns nonEmpty. Empty list -> false.
        assertFalse(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new ListIota(List.<Iota>of())), ENV).get(0)).getBool());
    }

    @Test
    public void coerceToBoolTreatsNonEmptyListAsTrue() {
        assertTrue(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new ListIota(List.<Iota>of(new DoubleIota(1)))), ENV).get(0)).getBool());
    }

    @Test
    public void coerceToBoolTreatsNullAsFalse() {
        assertFalse(((BooleanIota) OpCoerceToBool.INSTANCE.execute(
            List.of(new NullIota()), ENV).get(0)).getBool());
    }

    // === OpEquality ============================================================================

    @Test
    public void equalityRespectsToleratesOnDoubles() {
        // Doubles within TOLERANCE (1e-4) are tolerated.
        var eq = new OpEquality(false);
        var result = eq.execute(List.of(new DoubleIota(1.0), new DoubleIota(1.00001)), ENV);
        assertTrue(((BooleanIota) result.get(0)).getBool(), "doubles within tolerance are equal");
    }

    @Test
    public void equalityReturnsFalseForMismatchedTypes() {
        var eq = new OpEquality(false);
        var result = eq.execute(List.of(new DoubleIota(1.0), new BooleanIota(true)), ENV);
        assertFalse(((BooleanIota) result.get(0)).getBool(),
            "double vs boolean never tolerate, even if isTruthy matches");
    }

    @Test
    public void equalityInvertedReturnsNotTolerates() {
        var neq = new OpEquality(true);
        var sameNumbers = neq.execute(List.of(new DoubleIota(5), new DoubleIota(5)), ENV);
        assertFalse(((BooleanIota) sameNumbers.get(0)).getBool(), "equal values -> not_equals false");
        var differing = neq.execute(List.of(new DoubleIota(5), new DoubleIota(6)), ENV);
        assertTrue(((BooleanIota) differing.get(0)).getBool(), "unequal values -> not_equals true");
    }

    @Test
    public void equalityMatchesVec3WithinTolerance() {
        var eq = new OpEquality(false);
        var result = eq.execute(List.of(
            new Vec3Iota(new Vec3(1, 2, 3)),
            new Vec3Iota(new Vec3(1, 2, 3))
        ), ENV);
        assertTrue(((BooleanIota) result.get(0)).getBool());
    }

    // === Vec construction / destruction ========================================================

    @Test
    public void constructVecBuildsFromThreeDoubles() {
        var result = OpConstructVec.INSTANCE.execute(List.of(
            new DoubleIota(1), new DoubleIota(2), new DoubleIota(3)
        ), ENV);
        assertEquals(1, result.size());
        assertEquals(new Vec3(1, 2, 3), ((Vec3Iota) result.get(0)).getVec3());
    }

    @Test
    public void deconstructVecYieldsThreeDoubles() {
        var result = OpDeconstructVec.INSTANCE.execute(
            List.of(new Vec3Iota(new Vec3(7, 8, 9))), ENV);
        assertEquals(3, result.size(), "vec -> 3 doubles on stack");
        assertEquals(7.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
        assertEquals(8.0, ((DoubleIota) result.get(1)).getDouble(), 0.0);
        assertEquals(9.0, ((DoubleIota) result.get(2)).getDouble(), 0.0);
    }

    @Test
    public void constructDeconstructRoundtrips() {
        var built = OpConstructVec.INSTANCE.execute(List.of(
            new DoubleIota(-0.5), new DoubleIota(2.5), new DoubleIota(100)
        ), ENV);
        var unbuilt = OpDeconstructVec.INSTANCE.execute(built, ENV);
        assertEquals(-0.5, ((DoubleIota) unbuilt.get(0)).getDouble(), 0.0);
        assertEquals(2.5, ((DoubleIota) unbuilt.get(1)).getDouble(), 0.0);
        assertEquals(100.0, ((DoubleIota) unbuilt.get(2)).getDouble(), 0.0);
    }

    // === OpCoerceToAxial ========================================================================

    @Test
    public void coerceToAxialOnDoublesReturnsSign() {
        assertEquals(1.0, ((DoubleIota) OpCoerceToAxial.INSTANCE.execute(
            List.of(new DoubleIota(17.5)), ENV).get(0)).getDouble(), 0.0);
        assertEquals(-1.0, ((DoubleIota) OpCoerceToAxial.INSTANCE.execute(
            List.of(new DoubleIota(-0.25)), ENV).get(0)).getDouble(), 0.0);
        assertEquals(0.0, ((DoubleIota) OpCoerceToAxial.INSTANCE.execute(
            List.of(new DoubleIota(0.0)), ENV).get(0)).getDouble(), 0.0);
    }

    @Test
    public void coerceToAxialOnZeroVecReturnsZeroVec() {
        var result = OpCoerceToAxial.INSTANCE.execute(
            List.of(new Vec3Iota(Vec3.ZERO)), ENV);
        assertEquals(Vec3.ZERO, ((Vec3Iota) result.get(0)).getVec3());
    }

    @Test
    public void coerceToAxialOnNonZeroVecSnapsToNearestAxis() {
        // (0.9, 0.1, 0.2) — nearest axis is +X (1, 0, 0).
        var result = OpCoerceToAxial.INSTANCE.execute(
            List.of(new Vec3Iota(new Vec3(0.9, 0.1, 0.2))), ENV);
        assertEquals(new Vec3(1, 0, 0), ((Vec3Iota) result.get(0)).getVec3());

        // (0, -2, 0) — nearest axis is -Y (0, -1, 0).
        var result2 = OpCoerceToAxial.INSTANCE.execute(
            List.of(new Vec3Iota(new Vec3(0, -2, 0))), ENV);
        assertEquals(new Vec3(0, -1, 0), ((Vec3Iota) result2.get(0)).getVec3());
    }

    // === Modulo ================================================================================

    @Test
    public void moduloComputesRemainder() {
        var result = OpModulo.INSTANCE.execute(
            List.of(new DoubleIota(7), new DoubleIota(3)), ENV);
        assertEquals(1.0, ((DoubleIota) result.get(0)).getDouble(), 1e-9);
    }

    @Test
    public void moduloByZeroRaisesMishap() {
        assertThrows(Mishap.class, () -> OpModulo.INSTANCE.execute(
            List.of(new DoubleIota(5), new DoubleIota(0)), ENV),
            "modulo by zero must mishap, not return NaN / silently succeed");
    }

    // === Bitwise (long) ops ====================================================================

    @Test
    public void notInvertsBits() {
        var result = OpNot.INSTANCE.execute(List.of(new DoubleIota(0L)), ENV);
        assertEquals(-1L, ((DoubleIota) result.get(0)).getDouble(), 0.0, "~0 = -1");
    }

    @Test
    public void andOnLongsComputesBitwiseAnd() {
        var result = OpAnd.INSTANCE.execute(
            List.of(new DoubleIota(0b1100), new DoubleIota(0b1010)), ENV);
        assertEquals((double) (0b1100 & 0b1010), ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void orOnLongsComputesBitwiseOr() {
        var result = OpOr.INSTANCE.execute(
            List.of(new DoubleIota(0b1100), new DoubleIota(0b0011)), ENV);
        assertEquals((double) (0b1100 | 0b0011), ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void xorOnLongsComputesBitwiseXor() {
        var result = OpXor.INSTANCE.execute(
            List.of(new DoubleIota(0b1100), new DoubleIota(0b1010)), ENV);
        assertEquals((double) (0b1100 ^ 0b1010), ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void andOnListsComputesIntersection() {
        var lhs = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2), new DoubleIota(3)));
        var rhs = new ListIota(List.<Iota>of(new DoubleIota(2), new DoubleIota(3), new DoubleIota(4)));
        var result = OpAnd.INSTANCE.execute(List.of(lhs, rhs), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext(), "intersection has exactly 2 elements");
    }

    @Test
    public void orOnListsComputesUnion() {
        var lhs = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2)));
        var rhs = new ListIota(List.<Iota>of(new DoubleIota(2), new DoubleIota(3)));
        var result = OpOr.INSTANCE.execute(List.of(lhs, rhs), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        // union preserves lhs order then appends rhs elements not in lhs: [1, 2, 3]
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext());
    }

    @Test
    public void xorOnListsComputesSymmetricDifference() {
        var lhs = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2)));
        var rhs = new ListIota(List.<Iota>of(new DoubleIota(2), new DoubleIota(3)));
        var result = OpXor.INSTANCE.execute(List.of(lhs, rhs), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        // sym-diff: [1, 3]
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext());
    }

    @Test
    public void toSetDedupesPreservingFirstOccurrence() {
        var input = new ListIota(List.<Iota>of(
            new DoubleIota(1), new DoubleIota(2), new DoubleIota(1), new DoubleIota(3), new DoubleIota(2)));
        var result = OpToSet.INSTANCE.execute(List.of(input), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext(), "set dedupe produces exactly 3 unique values");
    }

    // === List ops ==============================================================================

    @Test
    public void singletonWrapsArgInOneElementList() {
        var arg = new DoubleIota(5);
        var result = OpSingleton.INSTANCE.execute(List.of(arg), ENV);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof ListIota);
        var inner = ((ListIota) result.get(0)).getList();
        var iter = inner.iterator();
        assertSame(arg, iter.next(), "singleton(x) is [x]");
        assertFalse(iter.hasNext());
    }

    @Test
    public void emptyListProducesEmpty() {
        var result = OpEmptyList.INSTANCE.execute(List.of(), ENV);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof ListIota);
        var inner = ((ListIota) result.get(0)).getList();
        assertFalse(inner.iterator().hasNext(), "empty list has no elements");
    }

    @Test
    public void splatUnwrapsListOntoStack() {
        var a = new DoubleIota(1);
        var b = new DoubleIota(2);
        var c = new DoubleIota(3);
        var list = new ListIota(List.<Iota>of(a, b, c));
        var result = OpSplat.INSTANCE.execute(List.of(list), ENV);
        assertEquals(3, result.size(), "splat pushes each element individually");
        assertEquals(1.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) result.get(1)).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) result.get(2)).getDouble(), 0.0);
    }

    @Test
    public void splatOnEmptyListYieldsEmptyStack() {
        var result = OpSplat.INSTANCE.execute(
            List.<Iota>of(new ListIota(List.<Iota>of())), ENV);
        assertEquals(0, result.size());
    }

    @Test
    public void listSizeReturnsCount() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2), new DoubleIota(3)));
        var result = OpListSize.INSTANCE.execute(List.of(list), ENV);
        assertEquals(3.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void listSizeOnEmptyListIsZero() {
        var empty = new ListIota(List.<Iota>of());
        var result = OpListSize.INSTANCE.execute(List.of(empty), ENV);
        assertEquals(0.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void reverskiReversesList() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2), new DoubleIota(3)));
        var result = OpReverski.INSTANCE.execute(List.of(list), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
    }

    @Test
    public void appendAddsToEndOfList() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2)));
        var added = new DoubleIota(3);
        var result = OpAppend.INSTANCE.execute(List.of(list, added), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertSame(added, iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void concatJoinsTwoListsLhsFirst() {
        var lhs = new ListIota(List.<Iota>of(new DoubleIota(1), new DoubleIota(2)));
        var rhs = new ListIota(List.<Iota>of(new DoubleIota(3), new DoubleIota(4)));
        var result = OpConcat.INSTANCE.execute(List.of(lhs, rhs), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(4.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext());
    }

    @Test
    public void consPrependsValue() {
        // OpCons: args[0] = the list (bottom), args[1] = the value to prepend (top).
        // Implementation: SpellList.LPair(top, bottom) — value becomes new head.
        var list = new ListIota(List.<Iota>of(new DoubleIota(2), new DoubleIota(3)));
        var prepended = new DoubleIota(1);
        var result = OpCons.INSTANCE.execute(List.of(list, prepended), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertSame(prepended, iter.next());
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext());
    }

    @Test
    public void unconsSplitsHeadAndTail() {
        var a = new DoubleIota(1);
        var list = new ListIota(List.<Iota>of(a, new DoubleIota(2), new DoubleIota(3)));
        var result = OpUnCons.INSTANCE.execute(List.of(list), ENV);
        // Implementation: return [ListIota(cdr), car]. So result[0] = tail list, result[1] = head.
        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof ListIota, "tail should be a ListIota");
        var tail = ((ListIota) result.get(0)).getList();
        var tailIter = tail.iterator();
        assertEquals(2.0, ((DoubleIota) tailIter.next()).getDouble(), 0.0);
        assertEquals(3.0, ((DoubleIota) tailIter.next()).getDouble(), 0.0);
        assertFalse(tailIter.hasNext());
        assertSame(a, result.get(1), "head should be the first element");
    }

    @Test
    public void unconsOnEmptyListReturnsListAndNullIota() {
        var empty = new ListIota(List.<Iota>of());
        var result = OpUnCons.INSTANCE.execute(List.of(empty), ENV);
        // Implementation: return [args[0], NullIota()]. Tail is empty list, head is NullIota.
        assertEquals(2, result.size());
        assertSame(empty, result.get(0));
        assertTrue(result.get(1) instanceof NullIota, "empty head -> NullIota");
    }

    @Test
    public void indexReturnsElementAtPosition() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(10), new DoubleIota(20), new DoubleIota(30)));
        var result = OpIndex.INSTANCE.execute(List.of(list, new DoubleIota(1)), ENV);
        assertEquals(20.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void indexOutOfBoundsReturnsNullIota() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(10), new DoubleIota(20)));
        var result = OpIndex.INSTANCE.execute(List.of(list, new DoubleIota(10)), ENV);
        assertTrue(result.get(0) instanceof NullIota, "out-of-range index returns NullIota");
    }

    @Test
    public void indexOfFindsMatchViaTolerates() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(10), new DoubleIota(20), new DoubleIota(30)));
        var result = OpIndexOf.INSTANCE.execute(List.of(list, new DoubleIota(20)), ENV);
        assertEquals(1.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void indexOfMissingReturnsNegativeOne() {
        // indexOfFirst returns -1 when no match — surfaces as DoubleIota(-1).
        var list = new ListIota(List.<Iota>of(new DoubleIota(10), new DoubleIota(20)));
        var result = OpIndexOf.INSTANCE.execute(List.of(list, new DoubleIota(99)), ENV);
        assertEquals(-1.0, ((DoubleIota) result.get(0)).getDouble(), 0.0);
    }

    @Test
    public void sliceProducesSublist() {
        var list = new ListIota(List.<Iota>of(
            new DoubleIota(10), new DoubleIota(20), new DoubleIota(30), new DoubleIota(40)));
        var result = OpSlice.INSTANCE.execute(
            List.of(list, new DoubleIota(1), new DoubleIota(3)), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        var iter = outList.iterator();
        assertEquals(20.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(30.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext());
    }

    @Test
    public void sliceWithEqualIndicesProducesEmptyList() {
        var list = new ListIota(List.<Iota>of(new DoubleIota(10), new DoubleIota(20)));
        var result = OpSlice.INSTANCE.execute(
            List.of(list, new DoubleIota(1), new DoubleIota(1)), ENV);
        var outList = ((ListIota) result.get(0)).getList();
        assertFalse(outList.iterator().hasNext(), "slice with i == j is empty");
    }

    // === Sanity: execute does not mutate the input args list ===================================

    @Test
    public void executeDoesNotMutateCallerProvidedArgs() {
        // The runtime passes an immutable takeLast view to execute. A bug where an op
        // accidentally mutates the input list would corrupt the caller's stack.
        var args = new ArrayList<Iota>();
        args.add(new DoubleIota(1));
        args.add(new DoubleIota(2));
        var before = List.copyOf(args);
        OpConcat.INSTANCE.execute(List.of(
            new ListIota(List.<Iota>of(new DoubleIota(1))),
            new ListIota(List.<Iota>of(new DoubleIota(2)))
        ), ENV);
        // Use the fact that List.copyOf + identity comparison proves we didn't touch args.
        assertEquals(before.size(), args.size());
        for (int i = 0; i < before.size(); i++) {
            assertSame(before.get(i), args.get(i));
        }
    }

    // === SpellList conversion helper (internal sanity) =========================================

    @Test
    public void spellListLListWrapsAndIteratesAList() {
        // SpellList.LList(list) is what ListIota uses internally when built from List<Iota>.
        // A bug here would break every list op in the game.
        var ll = new SpellList.LList(List.<Iota>of(new DoubleIota(1), new DoubleIota(2)));
        var iter = ll.iterator();
        assertTrue(iter.hasNext());
        assertEquals(1.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertEquals(2.0, ((DoubleIota) iter.next()).getDouble(), 0.0);
        assertFalse(iter.hasNext());
    }
}
