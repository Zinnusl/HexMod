package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.common.casting.arithmetic.BoolArithmetic;
import at.petrak.hexcasting.common.casting.arithmetic.DoubleArithmetic;
import at.petrak.hexcasting.common.casting.arithmetic.Vec3Arithmetic;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hex's arithmetic system dispatches spellbook operations (+, -, *, /, &&, ||, sin…) to typed
 * {@link Arithmetic} implementations. Each impl must recognize a known set of patterns and return
 * an {@link Operator} — silently returning null or throwing on a known pattern would break the
 * relevant math operators in-game.
 * <p>
 * We hit each arithmetic's getOperator with the patterns it claims to support (via opTypes()),
 * and confirm a non-null Operator comes back. This smoke-test would have caught the case where a
 * refactor accidentally dropped a when-branch in the Kotlin {@code when} table.
 */
public final class ArithmeticTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void doubleArithmeticCoversAllItsOps() {
        // If getOperator returns null / throws for one of its own declared patterns, that math
        // operator is silently broken in-game. Iterate over opTypes() and poke each one.
        for (var pattern : DoubleArithmetic.INSTANCE.opTypes()) {
            var op = DoubleArithmetic.INSTANCE.getOperator(pattern);
            assertNotNull(op, () -> "DoubleArithmetic.getOperator(" + pattern.anglesSignature()
                + "," + pattern.getStartDir() + ") returned null");
        }
    }

    @Test
    public void vec3ArithmeticCoversAllItsOps() {
        for (var pattern : Vec3Arithmetic.INSTANCE.opTypes()) {
            var op = Vec3Arithmetic.INSTANCE.getOperator(pattern);
            assertNotNull(op, () -> "Vec3Arithmetic.getOperator(" + pattern.anglesSignature()
                + "," + pattern.getStartDir() + ") returned null");
        }
    }

    @Test
    public void boolArithmeticCoversAllItsOps() {
        for (var pattern : BoolArithmetic.INSTANCE.opTypes()) {
            var op = BoolArithmetic.INSTANCE.getOperator(pattern);
            assertNotNull(op, () -> "BoolArithmetic.getOperator(" + pattern.anglesSignature()
                + "," + pattern.getStartDir() + ") returned null");
        }
    }

    @Test
    public void arithmeticInstancesHaveStableNames() {
        // arithName() feeds error messages + tag lookups. A null or empty name would break
        // downstream reporting.
        assertNotNull(DoubleArithmetic.INSTANCE.arithName());
        assertFalse(DoubleArithmetic.INSTANCE.arithName().isEmpty());
        assertNotNull(Vec3Arithmetic.INSTANCE.arithName());
        assertFalse(Vec3Arithmetic.INSTANCE.arithName().isEmpty());
        assertNotNull(BoolArithmetic.INSTANCE.arithName());
        assertFalse(BoolArithmetic.INSTANCE.arithName().isEmpty());
    }

    @Test
    public void arithmeticsDispatchToKnownAddPattern() {
        // Concretely test that ADD resolves: both Double and Vec3 should return an Operator for
        // the shared ADD pattern. If the Kotlin compile-time when-table has a typo in its key,
        // one of these would return null.
        var addPattern = at.petrak.hexcasting.api.casting.arithmetic.Arithmetic.ADD;
        assertNotNull(DoubleArithmetic.INSTANCE.getOperator(addPattern),
            "DoubleArithmetic must handle ADD");
        assertNotNull(Vec3Arithmetic.INSTANCE.getOperator(addPattern),
            "Vec3Arithmetic must handle ADD");
    }
}
