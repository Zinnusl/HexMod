package at.petrak.hexcasting.test;

import at.petrak.hexcasting.common.lib.HexBlockEntities;
import at.petrak.hexcasting.common.lib.HexParticles;
import at.petrak.hexcasting.common.recipe.HexRecipeStuffRegistry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Three more catalog-style registries whose entries would silently vanish if a static-init call
 * failed or a map-insertion was skipped:
 * <ul>
 *   <li>{@link HexParticles} — cosmetic but player-visible. A missing registration means the
 *       in-game particle appears as the default "missing" flame.</li>
 *   <li>{@link HexBlockEntities} — block-entity types. Wrong registration would make placed
 *       blocks fall back to their non-BE form and lose all their data.</li>
 *   <li>{@link HexRecipeStuffRegistry} — brainsweep and seal recipe serializers/types. Missing
 *       these means none of hex's recipes load from data packs.</li>
 * </ul>
 */
public final class RegistriesMiscTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void particlesAreInHexNamespace() {
        Map<ResourceLocation, ParticleType<?>> particles = new LinkedHashMap<>();
        HexParticles.registerParticles((part, loc) -> particles.put(loc, part));

        assertFalse(particles.isEmpty(), "HexParticles.registerParticles registered nothing");

        for (var e : particles.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null ParticleType");
            assertEquals("hexcasting", e.getKey().getNamespace(),
                () -> e.getKey() + ": wrong namespace");
        }

        assertTrue(particles.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "conjure_particle")),
            "conjure_particle is the main hex particle; missing it breaks spell feedback");
        assertSame(HexParticles.CONJURE_PARTICLE,
            particles.get(ResourceLocation.fromNamespaceAndPath("hexcasting", "conjure_particle")),
            "CONJURE_PARTICLE field is the same instance the register callback received");
    }

    @Test
    public void blockEntityTypesAreInHexNamespace() {
        // HexBlockEntities.<clinit> calls IXplatAbstractions.createBlockEntityType for each BE —
        // if the stub doesn't provide a working impl, class init explodes and every test fails.
        Map<ResourceLocation, BlockEntityType<?>> bes = new LinkedHashMap<>();
        HexBlockEntities.registerTiles((be, loc) -> bes.put(loc, be));

        assertFalse(bes.isEmpty(), "HexBlockEntities.registerTiles registered nothing");

        for (var e : bes.entrySet()) {
            assertNotNull(e.getValue(), () -> e.getKey() + " -> null BlockEntityType");
            assertEquals("hexcasting", e.getKey().getNamespace());
        }

        // Core block-entities the ritual circle relies on. A missing slate BE means no patterns
        // can be stored on slates → ritual circles do nothing.
        assertTrue(bes.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "slate")),
            "slate block-entity registered");
        assertTrue(bes.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "impetus/look")),
            "impetus/look BE registered");
        assertTrue(bes.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "akashic_bookshelf")),
            "akashic_bookshelf BE registered");
    }

    @Test
    public void recipeSerializersAreRegistered() {
        Map<ResourceLocation, RecipeSerializer<?>> sers = new LinkedHashMap<>();
        HexRecipeStuffRegistry.registerSerializers((ser, loc) -> sers.put(loc, ser));

        assertFalse(sers.isEmpty(), "HexRecipeStuffRegistry.registerSerializers registered nothing");
        assertTrue(sers.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "brainsweep")),
            "brainsweep serializer — drives every brainsweep recipe in the data pack");
        assertTrue(sers.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "seal_focus")),
            "seal_focus serializer");
        assertTrue(sers.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "seal_spellbook")),
            "seal_spellbook serializer");
    }

    @Test
    public void recipeTypesAreRegistered() {
        Map<ResourceLocation, RecipeType<?>> types = new LinkedHashMap<>();
        HexRecipeStuffRegistry.registerTypes((type, loc) -> types.put(loc, type));

        assertFalse(types.isEmpty(), "HexRecipeStuffRegistry.registerTypes registered nothing");
        assertTrue(types.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "brainsweep")),
            "brainsweep recipe type — the registry key recipes use to opt into the brainsweep loader");
        assertSame(HexRecipeStuffRegistry.BRAINSWEEP_TYPE,
            types.get(ResourceLocation.fromNamespaceAndPath("hexcasting", "brainsweep")),
            "BRAINSWEEP_TYPE static is the same instance as the map holds");
    }

    @Test
    public void recipeTypesHaveStableToString() {
        // toString() on anonymous RecipeType<?> is how vanilla shows the type id in /data output
        // and recipe viewers. Missing the namespace would break those tools.
        assertEquals("hexcasting:brainsweep", HexRecipeStuffRegistry.BRAINSWEEP_TYPE.toString(),
            "BRAINSWEEP_TYPE.toString() must include the hex namespace");
    }
}
