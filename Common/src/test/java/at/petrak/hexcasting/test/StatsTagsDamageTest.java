package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.mod.HexStatistics;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.common.lib.HexDamageTypes;
import at.petrak.hexcasting.common.lib.HexSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auxiliary catalogs that live outside the main action/iota/arithmetic flow but still need to
 * exist under known ids:
 * <ul>
 *   <li>{@link HexStatistics} — per-player trackers (media used, spells cast). Keyed into
 *       vanilla's {@code BuiltInRegistries.CUSTOM_STAT}.</li>
 *   <li>{@link HexTags} — TagKey constants for all three registry families (item, block, entity,
 *       action). A typo in the namespace portion would make every data-pack tag file miss.</li>
 *   <li>{@link HexDamageTypes} — OVERCAST is the type used when the player spends health to cast.
 *       A missing key would crash the overcast path (already hit during the port — documented in
 *       PlayerBasedCastEnv).</li>
 *   <li>{@link HexSounds} — sound catalog. Silent sounds = silent game; regression would be
 *       immediately noticeable but still worth a smoke-test at registration time.</li>
 * </ul>
 */
public final class StatsTagsDamageTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void customStatisticsAreInHexNamespace() {
        // Stats are keyed by ResourceLocation into the vanilla CUSTOM_STAT registry. A typo in
        // the namespace would mean the stat never shows up in the F3-stats screen.
        assertNotNull(HexStatistics.MEDIA_USED);
        assertNotNull(HexStatistics.MEDIA_OVERCAST);
        assertNotNull(HexStatistics.PATTERNS_DRAWN);
        assertNotNull(HexStatistics.SPELLS_CAST);

        assertEquals("hexcasting", HexStatistics.MEDIA_USED.getNamespace(),
            "MEDIA_USED registered under hex namespace");
        assertEquals("media_used", HexStatistics.MEDIA_USED.getPath(),
            "MEDIA_USED path literal is load-bearing — don't rename");
        assertEquals("media_overcast", HexStatistics.MEDIA_OVERCAST.getPath());
        assertEquals("patterns_drawn", HexStatistics.PATTERNS_DRAWN.getPath());
        assertEquals("spells_cast", HexStatistics.SPELLS_CAST.getPath());
    }

    @Test
    public void itemTagsAreInHexNamespace() {
        // A wrong namespace on a tag key means the data-pack tag files (which live under
        // data/hexcasting/tags/...) silently never load.
        assertEquals("hexcasting", HexTags.Items.STAVES.location().getNamespace());
        assertEquals("staves", HexTags.Items.STAVES.location().getPath());
        assertEquals("hexcasting", HexTags.Items.PHIAL_BASE.location().getNamespace());
        assertEquals("hexcasting", HexTags.Items.GRANTS_ROOT_ADVANCEMENT.location().getNamespace());
        assertEquals("hexcasting", HexTags.Items.IMPETI.location().getNamespace());
        assertEquals("hexcasting", HexTags.Items.DIRECTRICES.location().getNamespace());
    }

    @Test
    public void blockTagsAreInHexNamespace() {
        assertEquals("hexcasting", HexTags.Blocks.IMPETI.location().getNamespace());
        assertEquals("hexcasting", HexTags.Blocks.WATER_PLANTS.location().getNamespace());
        assertEquals("water_plants", HexTags.Blocks.WATER_PLANTS.location().getPath(),
            "WATER_PLANTS path — referenced by OpDestroyFluid");
        assertEquals("hexcasting", HexTags.Blocks.CHEAP_TO_BREAK_BLOCK.location().getNamespace());
    }

    @Test
    public void entityTagsAreInHexNamespace() {
        assertEquals("hexcasting", HexTags.Entities.NO_BRAINSWEEPING.location().getNamespace());
        assertEquals("cannot_brainsweep", HexTags.Entities.NO_BRAINSWEEPING.location().getPath(),
            "path literal referenced in data/hexcasting/tags/entity_type");
        assertEquals("hexcasting", HexTags.Entities.STICKY_TELEPORTERS.location().getNamespace());
        assertEquals("hexcasting", HexTags.Entities.CANNOT_TELEPORT.location().getNamespace());
    }

    @Test
    public void actionTagsAreInHexActionRegistry() {
        // Action-scoped tags are a 1.21 hex-specific quirk — they live under the hex:action
        // custom registry. A wrong registry key would make the tag never match anything.
        assertEquals("hexcasting", HexTags.Actions.REQUIRES_ENLIGHTENMENT.location().getNamespace());
        assertEquals("requires_enlightenment", HexTags.Actions.REQUIRES_ENLIGHTENMENT.location().getPath());
        assertEquals("per_world_pattern", HexTags.Actions.PER_WORLD_PATTERN.location().getPath());
        assertEquals("cannot_modify_cost", HexTags.Actions.CANNOT_MODIFY_COST.location().getPath(),
            "CANNOT_MODIFY_COST path is referenced by PlayerBasedCastEnv.getCostModifier");
        assertEquals("can_start_enlighten", HexTags.Actions.CAN_START_ENLIGHTEN.location().getPath());
    }

    @Test
    public void overcastDamageTypeIsNamedCorrectly() {
        // Used in PlayerBasedCastEnv.extractMediaFromInventory when casting from health. Missing
        // or wrong id => NPE on overcast, which was the exact regression that crashed staff use
        // during the port.
        assertNotNull(HexDamageTypes.OVERCAST);
        assertEquals("hexcasting", HexDamageTypes.OVERCAST.location().getNamespace());
        assertEquals("overcast", HexDamageTypes.OVERCAST.location().getPath());
    }

    @Test
    public void soundsRegistryIsNonEmpty() {
        // Catalog-shape sanity — the static-init must populate the map. If it doesn't, no sound
        // ever plays in-game.
        Map<ResourceLocation, SoundEvent> sounds = new LinkedHashMap<>();
        HexSounds.registerSounds((sound, loc) -> sounds.put(loc, sound));

        assertFalse(sounds.isEmpty(), "HexSounds.registerSounds registered nothing");

        for (var e : sounds.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null SoundEvent");
            assertEquals("hexcasting", e.getKey().getNamespace(),
                () -> e.getKey() + ": wrong namespace");
        }
    }

    @Test
    public void loadBearingSoundsArePresent() {
        // CAST_NORMAL is the default eval sound referenced everywhere; CAST_FAILURE is used on
        // every mishap. Regression here would turn all casting silent.
        Map<ResourceLocation, SoundEvent> sounds = new LinkedHashMap<>();
        HexSounds.registerSounds((sound, loc) -> sounds.put(loc, sound));

        assertTrue(sounds.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "casting.cast.normal")),
            "casting.cast.normal — default cast sound");
        assertTrue(sounds.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "casting.cast.fail")),
            "casting.cast.fail — mishap sound");
        assertTrue(sounds.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "spellcircle.find_block")),
            "spellcircle.find_block — ritual step sound");
    }
}
