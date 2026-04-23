package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every spell action declared in {@link HexActions} must:
 * <ul>
 *   <li>have a non-null {@link ActionRegistryEntry} (null would crash on cast)</li>
 *   <li>have a non-null underlying {@code Action}</li>
 *   <li>have a non-null canonical {@code HexPattern}</li>
 *   <li>have a modloc ResourceLocation id in the hex namespace</li>
 * </ul>
 * <p>
 * Bypasses {@code IXplatAbstractions.INSTANCE.getActionRegistry()} (which requires
 * a live platform init) by walking {@code HexActions.register(BiConsumer)} directly —
 * that's the same entry-point every platform calls to populate its registry.
 */
public final class AllSpellsRegistrationTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void everyActionHasNonNullFields() {
        Map<ResourceLocation, ActionRegistryEntry> all = new LinkedHashMap<>();
        HexActions.register((entry, id) -> all.put(id, entry));

        assertFalse(all.isEmpty(),
            "HexActions.register registered nothing — static init or BiConsumer wiring broke");

        for (var e : all.entrySet()) {
            var id = e.getKey();
            var entry = e.getValue();

            assertNotNull(entry, () -> id + ": ActionRegistryEntry is null");
            assertNotNull(entry.action(), () -> id + ": entry.action() is null");
            assertNotNull(entry.prototype(), () -> id + ": entry.prototype() pattern is null");
            assertEquals(HexAPI.MOD_ID, id.getNamespace(),
                () -> id + ": registered under non-hex namespace");
            assertFalse(id.getPath().isEmpty(), () -> id + ": empty action path");
        }
    }

    @Test
    public void noDuplicatePatternSignatures() {
        // Two actions with the same (pattern, startDir) would both match the same
        // drawn shape — only one would ever win. Guards against copy-paste regressions
        // in HexActions.make().
        Map<String, ResourceLocation> bySig = new LinkedHashMap<>();
        HexActions.register((entry, id) -> {
            var pat = entry.prototype();
            var sig = pat.getStartDir().name() + ":" + pat.anglesSignature();
            var prior = bySig.put(sig, id);
            assertNull(prior, () -> "duplicate pattern signature '" + sig + "' on " + id + " and " + prior);
        });
    }

    @Test
    public void everyActionHasRoundtrippablePattern() {
        HexActions.register((entry, id) -> {
            var pat = entry.prototype();
            var roundtrip = at.petrak.hexcasting.api.casting.math.HexPattern.fromNBT(pat.serializeToNBT());
            assertEquals(pat, roundtrip,
                () -> id + ": pattern fails NBT round-trip (signature=" + pat.anglesSignature()
                    + ", startDir=" + pat.getStartDir() + ")");
        });
    }
}
