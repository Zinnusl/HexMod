package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaPredicate;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.player.AltioraAbility;
import at.petrak.hexcasting.api.player.FlightAbility;
import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Player state records that ride on attachment / ServerPlayer NBT:
 * <ul>
 *   <li>{@link Sentinel} — position + dimension + extends-range flag.</li>
 *   <li>{@link FlightAbility} — timeLeft, origin, radius with sentinel values for infinite.</li>
 *   <li>{@link AltioraAbility} — elytra grace period.</li>
 * </ul>
 * Plus the {@link IotaPredicate} sealed-ish hierarchy used on every operator dispatch.
 */
public final class PlayerAttachmentStateTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void sentinelPreservesAllThreeFields() {
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        var s = new Sentinel(true, new Vec3(10, 64, -5), dim);
        assertTrue(s.extendsRange(), "extendsRange preserved");
        assertEquals(new Vec3(10, 64, -5), s.position(), "position preserved");
        assertEquals(dim, s.dimension(), "dimension preserved");

        var noExtend = new Sentinel(false, Vec3.ZERO, dim);
        assertFalse(noExtend.extendsRange());
    }

    @Test
    public void flightAbilityInfiniteSentinels() {
        // Contract: timeLeft = -1 means "infinite flight time"; radius < 0 means "infinite radius".
        // PlayerBasedCastEnv.isVecInRangeEnvironment uses radius, so a regression that treated -1
        // as a literal would clamp flight to 1 block radius.
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        var infiniteTime = new FlightAbility(-1, dim, Vec3.ZERO, 128.0);
        assertEquals(-1, infiniteTime.timeLeft(), "-1 sentinel for infinite time");
        var infiniteRadius = new FlightAbility(100, dim, Vec3.ZERO, -1.0);
        assertTrue(infiniteRadius.radius() < 0, "negative radius is infinite");
    }

    @Test
    public void flightAbilityRecordEqualityIncludesAllFields() {
        // Records use structural equals; state comparison in attachment dirty-checking
        // depends on this.
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        var a = new FlightAbility(100, dim, new Vec3(0, 64, 0), 32.0);
        var b = new FlightAbility(100, dim, new Vec3(0, 64, 0), 32.0);
        var differentOrigin = new FlightAbility(100, dim, new Vec3(1, 64, 0), 32.0);
        assertEquals(a, b, "same-field records are equal");
        assertNotEquals(a, differentOrigin, "different origin is not equal");
    }

    @Test
    public void altioraAbilityIsSingleFieldRecord() {
        var a = new AltioraAbility(20);
        assertEquals(20, a.gracePeriod(), "gracePeriod preserved");
        assertEquals(new AltioraAbility(20), a, "record equality by field");
        assertNotEquals(new AltioraAbility(0), a);
    }

    @Test
    public void iotaPredicateOfTypeMatchesOnlyThatType() {
        var isDouble = IotaPredicate.ofType(HexIotaTypes.DOUBLE);
        assertTrue(isDouble.test(new DoubleIota(1.0)), "DoubleIota matches ofType(DOUBLE)");
        assertFalse(isDouble.test(new BooleanIota(true)), "BooleanIota doesn't match ofType(DOUBLE)");
        assertFalse(isDouble.test(new NullIota()), "NullIota doesn't match ofType(DOUBLE)");
    }

    @Test
    public void iotaPredicateOrAcceptsEitherSide() {
        var isDouble = IotaPredicate.ofType(HexIotaTypes.DOUBLE);
        var isBool = IotaPredicate.ofType(HexIotaTypes.BOOLEAN);
        var either = IotaPredicate.or(isDouble, isBool);
        assertTrue(either.test(new DoubleIota(1.0)));
        assertTrue(either.test(new BooleanIota(false)));
        assertFalse(either.test(new NullIota()));
    }

    @Test
    public void iotaPredicateAnyVarArgsAcceptsAll() {
        var any = IotaPredicate.any(
            IotaPredicate.ofType(HexIotaTypes.DOUBLE),
            IotaPredicate.ofType(HexIotaTypes.BOOLEAN),
            IotaPredicate.ofType(HexIotaTypes.NULL)
        );
        assertTrue(any.test(new DoubleIota(1.0)));
        assertTrue(any.test(new BooleanIota(true)));
        assertTrue(any.test(new NullIota()));
        assertFalse(any.test(new at.petrak.hexcasting.api.casting.iota.Vec3Iota(Vec3.ZERO)),
            "Vec3Iota not in the any-list");
    }

    @Test
    public void iotaPredicateTrueAcceptsEverything() {
        var t = IotaPredicate.TRUE;
        assertTrue(t.test(new DoubleIota(0.0)));
        assertTrue(t.test(new BooleanIota(false)));
        assertTrue(t.test(new NullIota()));
    }

    @Test
    public void iotaPredicateOfTypeRecordEquality() {
        // IotaPredicate.OfType is a record → records with same IotaType should be equal. The
        // ArithmeticEngine cache relies on this for hash-cons dedup.
        var a = IotaPredicate.ofType(HexIotaTypes.DOUBLE);
        var b = IotaPredicate.ofType(HexIotaTypes.DOUBLE);
        assertEquals(a, b, "same-type OfType records equal");
        var c = IotaPredicate.ofType(HexIotaTypes.BOOLEAN);
        assertNotEquals(a, c, "different-type OfType records not equal");
    }
}
