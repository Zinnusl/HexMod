package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.eval.sideeffects.EvalSound;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.hex.HexArithmetics;
import at.petrak.hexcasting.common.lib.hex.HexContinuationTypes;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each of the hex "static registry catalogs" ({@link HexAttributes}, {@link HexArithmetics},
 * {@link HexEvalSounds}, {@link HexContinuationTypes}, {@link HexIotaTypes}) exposes a
 * {@code register(BiConsumer)} helper the platform wiring uses to feed entries into the
 * real registries at mod-init time. If any of these catalogs returns an empty map, or has a
 * duplicate id, or returns null values, the platform never registers those entries — and the
 * entire subsystem silently vanishes. These tests make the failure visible.
 */
public final class RegistryCatalogTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void attributesAreNonEmptyAndInHexNamespace() {
        Map<ResourceLocation, Attribute> attrs = new LinkedHashMap<>();
        HexAttributes.register((attr, loc) -> attrs.put(loc, attr));

        assertFalse(attrs.isEmpty(), "HexAttributes.register registered nothing");
        // Reference each expected attribute — if any is null / missing, fail loud.
        for (var e : attrs.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null attribute");
            assertEquals("hexcasting", e.getKey().getNamespace(),
                () -> e.getKey() + " registered in wrong namespace");
        }

        // Minimum set we rely on — not exhaustive, just load-bearing.
        assertTrue(attrs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "ambit_radius")),
            "ambit_radius registered");
        assertTrue(attrs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "sentinel_radius")),
            "sentinel_radius registered");
        assertTrue(attrs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "feeble_mind")),
            "feeble_mind registered — the attribute that crashed staff-use during the port");
        assertTrue(attrs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "media_consumption")),
            "media_consumption registered");
    }

    @Test
    public void arithmeticsAreNonEmpty() {
        Map<ResourceLocation, Arithmetic> ariths = new LinkedHashMap<>();
        HexArithmetics.register((arith, loc) -> ariths.put(loc, arith));

        assertFalse(ariths.isEmpty(), "HexArithmetics.register registered nothing");
        for (var e : ariths.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null Arithmetic");
            assertEquals("hexcasting", e.getKey().getNamespace());
        }

        assertTrue(ariths.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "double")),
            "double arithmetic registered");
        assertTrue(ariths.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "vec3")),
            "vec3 arithmetic registered");
        assertTrue(ariths.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "list")),
            "list arithmetic registered");
        assertTrue(ariths.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "bool")),
            "bool arithmetic registered");
    }

    @Test
    public void evalSoundsAreNonEmpty() {
        Map<ResourceLocation, EvalSound> sounds = new LinkedHashMap<>();
        HexEvalSounds.register((sound, loc) -> sounds.put(loc, sound));

        assertFalse(sounds.isEmpty(), "HexEvalSounds.register registered nothing");
        for (var e : sounds.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null EvalSound");
        }

        // NOTHING is the default — if it's missing, any action without a specific sound NPEs.
        assertTrue(sounds.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "nothing")),
            "NOTHING eval sound registered");
    }

    @Test
    public void continuationTypesAreNonEmpty() {
        Map<ResourceLocation, ContinuationFrame.Type<?>> types = new LinkedHashMap<>();
        HexContinuationTypes.registerContinuations((ctype, loc) -> types.put(loc, ctype));

        assertFalse(types.isEmpty(), "HexContinuationTypes.register registered nothing");
        for (var e : types.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null ContinuationFrame.Type");
        }
    }

    @Test
    public void iotaTypesAreNonEmpty() {
        Map<ResourceLocation, IotaType<?>> types = new LinkedHashMap<>();
        HexIotaTypes.registerTypes((itype, loc) -> types.put(loc, itype));

        assertFalse(types.isEmpty());
        for (var e : types.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null IotaType");
            // color() is part of the contract — serialized iotas are colored in tooltips.
            // 0 alpha would render invisibly — any IotaType returning 0 has a bug.
            assertNotEquals(0, e.getValue().color(), () -> e.getKey() + ": zero color");
        }

        // Primitive types the rest of the codebase assumes are always present.
        assertTrue(types.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "null")));
        assertTrue(types.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "double")));
        assertTrue(types.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "garbage")),
            "garbage type is the error fallback — missing it breaks deserialization");
    }
}
