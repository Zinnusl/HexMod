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
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// JUnit 5.6 (the version pinned by this project) doesn't have assertInstanceOf.

/**
 * Extra coverage on top of {@link IotaTypeRoundtripTest}: focuses on edge cases that silently
 * break in the wild — garbage fallback, extreme numeric values, NaN scrubbing, oversize lists,
 * deep recursion, and the exact on-disk shape (ByteTag) of a BooleanIota. If any of these
 * regress we'd either lose saved spells to corruption or deserialize into the wrong Iota class.
 */
public final class IotaTypeRoundtripExtraTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    private static Iota roundtrip(Iota original) {
        CompoundTag tag = IotaType.serialize(original);
        // world=null: every type exercised here is a primitive that doesn't look at the world.
        return IotaType.deserialize(tag, null);
    }

    private static List<Iota> toJavaList(SpellList sl) {
        var out = new ArrayList<Iota>();
        for (Iota i : sl) out.add(i);
        return out;
    }

    @Test
    public void garbageIotaDirectRoundtrip() {
        // GarbageIota is the fallback every malformed tag collapses to — but it's also a first-class
        // type you can serialize directly. Confirm that round-trip is stable: if a spellbook already
        // contains a GarbageIota, reading+writing it must stay garbage.
        var original = new GarbageIota();
        CompoundTag tag = IotaType.serialize(original);
        assertEquals("hexcasting:garbage", tag.getString(HexIotaTypes.KEY_TYPE),
            "garbage should serialize with the garbage type key");
        assertTrue(tag.contains(HexIotaTypes.KEY_DATA), "serialized tag should have a data key");
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof GarbageIota, "expected GarbageIota, got " + back.getClass().getSimpleName());
    }

    @Test
    public void deeplyNestedListIotaRoundtrip() {
        // 6 levels deep — still well under MAX_SERIALIZATION_DEPTH (256) but enough to exercise
        // recursion through IotaType.serialize/deserialize. A regression in the ListIota recursive
        // code path would explode here.
        Iota current = new DoubleIota(42.0);
        for (int i = 0; i < 6; i++) {
            current = new ListIota(List.of(current));
        }
        var back = roundtrip(current);
        // Walk the 6 levels back down and confirm the DoubleIota at the bottom survived.
        assertTrue(back instanceof ListIota, "expected outer ListIota");
        Iota cursor = back;
        for (int i = 0; i < 6; i++) {
            assertTrue(cursor instanceof ListIota, "level " + i + " should be a ListIota");
            var asList = toJavaList(((ListIota) cursor).getList());
            assertEquals(1, asList.size(), "level " + i + " should have exactly one child");
            cursor = asList.get(0);
        }
        assertTrue(cursor instanceof DoubleIota, "innermost should be DoubleIota, got " + cursor.getClass().getSimpleName());
        assertEquals(42.0, ((DoubleIota) cursor).getDouble(), 0.0);
    }

    @Test
    public void largeButBelowLimitListRoundtrip() {
        // 100 entries is nowhere near MAX_SERIALIZATION_TOTAL (1024) but large enough to confirm
        // ListTag handling scales — this would catch e.g. a silent truncation or a regression that
        // preserves only a subset of entries.
        var entries = new ArrayList<Iota>();
        for (int i = 0; i < 128; i++) {
            entries.add(new DoubleIota(i * 0.5));
        }
        var original = new ListIota(entries);
        var back = roundtrip(original);
        assertTrue(back instanceof ListIota);
        var backList = toJavaList(((ListIota) back).getList());
        assertEquals(128, backList.size(), "all entries should survive");
        for (int i = 0; i < 128; i++) {
            assertTrue(backList.get(i) instanceof DoubleIota, "entry " + i + " should be DoubleIota");
            assertEquals(i * 0.5, ((DoubleIota) backList.get(i)).getDouble(), 0.0,
                "entry " + i + " value preserved");
        }
    }

    @Test
    public void tooLargeListBecomesGarbageOnSerialize() {
        // MAX_SERIALIZATION_TOTAL = 1024. A list of 1500 DoubleIotas has size() >= 1501 which
        // trips the guard in IotaType.serialize — the result should be a GarbageIota-shaped tag,
        // not the original list's tag. This is the mechanism that prevents a runaway spell from
        // writing an effectively-unbounded NBT blob into a player's inventory.
        var entries = new ArrayList<Iota>();
        for (int i = 0; i < 1500; i++) {
            entries.add(new DoubleIota(i));
        }
        var huge = new ListIota(entries);
        // Sanity check: confirm our construction actually trips the guard rather than silently
        // falling below the threshold.
        assertTrue(IotaType.isTooLargeToSerialize(List.of(huge)),
            "test fixture should exceed MAX_SERIALIZATION_TOTAL");

        CompoundTag tag = IotaType.serialize(huge);
        assertEquals("hexcasting:garbage", tag.getString(HexIotaTypes.KEY_TYPE),
            "oversize list should be rewritten as garbage");

        // And deserializing that garbage tag must also hand back a GarbageIota, closing the loop.
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof GarbageIota, "oversize list must collapse to GarbageIota on round-trip");
    }

    @Test
    public void heterogeneousListRoundtrip() {
        // The types-per-slot test: a mixed list must come back with each slot's type intact.
        // Regressions here usually come from a shared-tag optimisation (e.g. writing one type key
        // for the whole list rather than per entry).
        var pat = HexPattern.fromAngles("aqaa", HexDir.EAST);
        var vec = new Vec3(0.5, -0.5, 0.0);
        var original = new ListIota(List.of(
            new NullIota(),
            new DoubleIota(7.5),
            new BooleanIota(true),
            new Vec3Iota(vec),
            new PatternIota(pat)
        ));
        var back = roundtrip(original);
        assertTrue(back instanceof ListIota);
        var backList = toJavaList(((ListIota) back).getList());
        assertEquals(5, backList.size());
        assertTrue(backList.get(0) instanceof NullIota, "slot 0 NullIota");
        assertTrue(backList.get(1) instanceof DoubleIota, "slot 1 DoubleIota");
        assertEquals(7.5, ((DoubleIota) backList.get(1)).getDouble(), 0.0);
        assertTrue(backList.get(2) instanceof BooleanIota, "slot 2 BooleanIota");
        assertTrue(((BooleanIota) backList.get(2)).getBool());
        assertTrue(backList.get(3) instanceof Vec3Iota, "slot 3 Vec3Iota");
        assertEquals(vec, ((Vec3Iota) backList.get(3)).getVec3());
        assertTrue(backList.get(4) instanceof PatternIota, "slot 4 PatternIota");
        var backPat = ((PatternIota) backList.get(4)).getPattern();
        assertEquals(pat.anglesSignature(), backPat.anglesSignature());
        assertEquals(pat.getStartDir(), backPat.getStartDir());
    }

    @Test
    public void vec3IotaExtremeValuesRoundtrip() {
        // Very large, integer-boundary, and mixed-sign doubles. Vec3 rides through a LongArrayTag
        // in some serialize paths (see Vec3Iota.deserialize) — these values stress that encoding.
        Vec3[] extremes = new Vec3[]{
            new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 0.0),
            new Vec3(1e300, -1e300, 1e-300),
            new Vec3((double) Integer.MAX_VALUE, (double) Integer.MIN_VALUE, (double) Long.MAX_VALUE),
            new Vec3(-0.0, 0.0, -0.0)
        };
        for (Vec3 v : extremes) {
            var back = roundtrip(new Vec3Iota(v));
            assertTrue(back instanceof Vec3Iota, "Vec3Iota(" + v + ") deserialized to " + back);
            var backV = ((Vec3Iota) back).getVec3();
            assertEquals(v.x, backV.x, 0.0, "x preserved for " + v);
            assertEquals(v.y, backV.y, 0.0, "y preserved for " + v);
            assertEquals(v.z, backV.z, 0.0, "z preserved for " + v);
        }
    }

    @Test
    public void doubleIotaNaNIsScrubbed() {
        // DoubleIota.getDouble() calls HexUtils.fixNAN, which maps NaN/Inf -> 0.0. That scrubbing
        // applies on serialize too (serialize() uses getDouble()), so a DoubleIota(NaN) must
        // round-trip to a DoubleIota(0.0). Without this, stacks of infinities could get stored
        // and later break arithmetic spells.
        var nanBack = roundtrip(new DoubleIota(Double.NaN));
        assertTrue(nanBack instanceof DoubleIota);
        assertEquals(0.0, ((DoubleIota) nanBack).getDouble(), 0.0, "NaN should scrub to 0.0");

        var posInfBack = roundtrip(new DoubleIota(Double.POSITIVE_INFINITY));
        assertTrue(posInfBack instanceof DoubleIota);
        assertEquals(0.0, ((DoubleIota) posInfBack).getDouble(), 0.0, "+Inf should scrub to 0.0");

        var negInfBack = roundtrip(new DoubleIota(Double.NEGATIVE_INFINITY));
        assertTrue(negInfBack instanceof DoubleIota);
        assertEquals(0.0, ((DoubleIota) negInfBack).getDouble(), 0.0, "-Inf should scrub to 0.0");
    }

    @Test
    public void booleanIotaWritesByteTag() {
        // BooleanIota's comment says "there is no boolean tag :(" — it writes a ByteTag instead.
        // The exact tag type matters for cross-version compatibility: if something ever
        // accidentally switched it to e.g. a StringTag, downcast() in BooleanIota.deserialize
        // would explode on every saved boolean.
        CompoundTag trueTag = IotaType.serialize(new BooleanIota(true));
        Tag trueData = trueTag.get(HexIotaTypes.KEY_DATA);
        assertNotNull(trueData, "boolean tag must have a data entry");
        assertTrue(trueData instanceof ByteTag, "boolean data should be a ByteTag, got " + trueData.getClass().getSimpleName());
        assertEquals((byte) 1, ((ByteTag) trueData).getAsByte(), "true encodes as 1");

        CompoundTag falseTag = IotaType.serialize(new BooleanIota(false));
        Tag falseData = falseTag.get(HexIotaTypes.KEY_DATA);
        assertNotNull(falseData, "boolean tag must have a data entry");
        assertTrue(falseData instanceof ByteTag, "boolean data should be a ByteTag, got " + falseData.getClass().getSimpleName());
        assertEquals((byte) 0, ((ByteTag) falseData).getAsByte(), "false encodes as 0");
    }
}
