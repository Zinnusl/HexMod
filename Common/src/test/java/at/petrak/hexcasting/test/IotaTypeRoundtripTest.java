package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.GarbageIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// JUnit 5.6 (the version pinned by this project) doesn't have assertInstanceOf.

/**
 * The iota serialization protocol is the single biggest compatibility surface of Hex: every stored
 * spell in every spellbook, trinket, cypher, and slate goes through
 * {@link IotaType#serialize(Iota)} / {@link IotaType#deserialize(CompoundTag, net.minecraft.server.level.ServerLevel)}.
 * <p>
 * On 1.20 iota NBT was written into {@code stack.getTag()}. On 1.21 it rides on
 * {@code minecraft:custom_data}. Any break in that NBT shape — whether from a tag name collision,
 * a lost type key, a codec swap — silently turns every player's saved spells into {@link GarbageIota}.
 * <p>
 * We exercise the types that don't need a world ({@link EntityIota} + {@link ContinuationIota}
 * are excluded because they resolve UUIDs / frame types through the server registry). If the world
 * passed to {@code deserialize} is null the type either ignores it (primitive types) or fails
 * fast — the types under test here are in the former bucket.
 */
public final class IotaTypeRoundtripTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void registryIsPopulated() {
        // Sanity check that TestBootstrap actually registered hex iota types. The other tests
        // all depend on HexIotaTypes.REGISTRY.getKey(type) returning a non-null id for the
        // serialize() step — if this fails, the whole suite breaks with a confusing NPE.
        var registry = IXplatAbstractions.INSTANCE.getIotaTypeRegistry();
        assertNotNull(registry.getKey(HexIotaTypes.NULL), "NULL not registered");
        assertNotNull(registry.getKey(HexIotaTypes.DOUBLE), "DOUBLE not registered");
        assertNotNull(registry.getKey(HexIotaTypes.PATTERN), "PATTERN not registered");

        // And the reverse lookup must return the actual type — HexIotaTypes.REGISTRY.get(loc)
        // is what IotaType.getTypeFromTag uses during deserialize.
        var loc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hexcasting", "double");
        var fetched = registry.get(loc);
        assertSame(HexIotaTypes.DOUBLE, fetched, "registry.get returned a different instance");
    }

    private static Iota roundtrip(Iota original) {
        CompoundTag tag = IotaType.serialize(original);
        // world=null: primitive iota types don't look at world. Entity/Continuation would.
        return IotaType.deserialize(tag, null);
    }

    @Test
    public void nullIotaRoundtrip() {
        var original = new NullIota();
        var back = roundtrip(original);
        assertTrue(back instanceof NullIota, "expected NullIota, got " + back.getClass().getSimpleName());
    }

    @Test
    public void doubleIotaRoundtrip() {
        for (double d : new double[]{0.0, -0.0, 1.0, -1.0, Math.PI, -Math.E, 1e-9, 1e9, Double.MIN_VALUE}) {
            var back = roundtrip(new DoubleIota(d));
            assertTrue(back instanceof DoubleIota, "DoubleIota(" + d + ") deserialized to " + back);
            assertEquals(d, ((DoubleIota) back).getDouble(), 0.0, "value preserved");
        }
    }

    @Test
    public void booleanIotaRoundtrip() {
        for (boolean b : new boolean[]{true, false}) {
            var back = roundtrip(new BooleanIota(b));
            assertTrue(back instanceof BooleanIota);
            assertEquals(b, ((BooleanIota) back).getBool());
        }
    }

    @Test
    public void vec3IotaRoundtrip() {
        var v = new Vec3(1.0, -2.5, 3.14);
        var back = roundtrip(new Vec3Iota(v));
        assertTrue(back instanceof Vec3Iota);
        assertEquals(v, ((Vec3Iota) back).getVec3());
    }

    @Test
    public void patternIotaRoundtrip() {
        // Each pattern shape + start direction pair is meaningful — both must survive.
        var pat = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var back = roundtrip(new PatternIota(pat));
        assertTrue(back instanceof PatternIota);
        var backPat = ((PatternIota) back).getPattern();
        assertEquals(pat.anglesSignature(), backPat.anglesSignature(), "angle signature preserved");
        assertEquals(pat.getStartDir(), backPat.getStartDir(), "start direction preserved");
    }

    @Test
    public void listIotaRoundtripNested() {
        // Lists nest arbitrarily — test a two-level list with heterogeneous primitives.
        var inner = new ListIota(List.of(new DoubleIota(1.5), new BooleanIota(true)));
        var outer = new ListIota(List.of(new NullIota(), inner, new DoubleIota(-2.0)));
        var back = roundtrip(outer);
        assertTrue(back instanceof ListIota);
        var backList = toJavaList(((ListIota) back).getList());
        assertEquals(3, backList.size(), "outer list size preserved");
        assertTrue(backList.get(0) instanceof NullIota);
        assertTrue(backList.get(1) instanceof ListIota);
        assertTrue(backList.get(2) instanceof DoubleIota);
        assertEquals(-2.0, ((DoubleIota) backList.get(2)).getDouble(), 0.0);

        var backInner = toJavaList(((ListIota) backList.get(1)).getList());
        assertEquals(2, backInner.size(), "inner list preserved through one level of nesting");
        assertEquals(1.5, ((DoubleIota) backInner.get(0)).getDouble(), 0.0);
        assertTrue(((BooleanIota) backInner.get(1)).getBool());
    }

    private static List<Iota> toJavaList(SpellList sl) {
        var out = new java.util.ArrayList<Iota>();
        for (Iota i : sl) out.add(i);
        return out;
    }

    @Test
    public void unknownTypeBecomesGarbage() {
        // If storage carries a type key the current version doesn't know, deserialization must
        // yield a GarbageIota — that's the contract the spellbook UI relies on. Failing this
        // silently would turn old saves into null pointers.
        CompoundTag tag = new CompoundTag();
        tag.putString(HexIotaTypes.KEY_TYPE, "hexcasting:nonexistent_type");
        CompoundTag data = new CompoundTag();
        tag.put(HexIotaTypes.KEY_DATA, data);
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof GarbageIota, "expected GarbageIota, got " + back.getClass().getSimpleName());
    }

    @Test
    public void missingDataTagBecomesGarbage() {
        CompoundTag tag = new CompoundTag();
        tag.putString(HexIotaTypes.KEY_TYPE, "hexcasting:double");
        // No KEY_DATA — deserialize should not crash, should return garbage.
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof GarbageIota, "expected GarbageIota, got " + back.getClass().getSimpleName());
    }

    @Test
    public void malformedTypeKeyBecomesGarbage() {
        // ResourceLocation.tryParse returns null for colons in the wrong place etc.
        CompoundTag tag = new CompoundTag();
        tag.putString(HexIotaTypes.KEY_TYPE, "not a valid: resource location");
        tag.put(HexIotaTypes.KEY_DATA, new CompoundTag());
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof GarbageIota, "expected GarbageIota, got " + back.getClass().getSimpleName());
    }
}
