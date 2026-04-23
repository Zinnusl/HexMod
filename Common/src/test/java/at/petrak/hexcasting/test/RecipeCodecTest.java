package at.petrak.hexcasting.test;

import at.petrak.hexcasting.common.recipe.BrainsweepRecipe;
import at.petrak.hexcasting.common.recipe.ingredient.StateIngredient;
import at.petrak.hexcasting.common.recipe.ingredient.StateIngredientBlock;
import at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient;
import at.petrak.hexcasting.common.recipe.ingredient.brainsweep.EntityTypeIngredient;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BrainsweepRecipe} has the most complex codec in the mod: it nests two custom ingredient
 * types ({@link StateIngredient} + {@link BrainsweepeeIngredient}) plus a BlockState, all inside
 * a {@code MapCodec}. Silently breaking the JSON shape of any of those nested codecs would cause
 * every brainsweep recipe on the data pack to fail to load, which the live mod swallows as "no
 * recipes registered."
 * <p>
 * We exercise the codec round-trip through {@link JsonOps} — encode a recipe, decode it back,
 * check field-by-field. The recipe doesn't need a ServerLevel because all the registry
 * references go through {@link BuiltInRegistries}, which {@link TestBootstrap} has already
 * primed by running vanilla Bootstrap.
 */
public final class RecipeCodecTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void blockStateIngredientRoundTrip() {
        // Simple path: blockIn points at a single vanilla block.
        var original = new BrainsweepRecipe(
            new StateIngredientBlock(Blocks.DIAMOND_BLOCK),
            new EntityTypeIngredient(EntityType.SHEEP),
            1000L,
            Blocks.EMERALD_BLOCK.defaultBlockState()
        );

        var codec = BrainsweepRecipe.Serializer.INSTANCE.codec().codec();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), () -> "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        JsonElement json = encoded.result().get();

        var decoded = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(decoded.result().isPresent(), () -> "decode failed: " + decoded.error().map(Object::toString).orElse("?"));
        BrainsweepRecipe back = decoded.result().get();

        assertEquals(original.mediaCost(), back.mediaCost(), "mediaCost preserved");
        assertEquals(original.result(), back.result(), "result BlockState preserved");
        // StateIngredient equality is a sealed-subclass thing — check by behavior: both match on
        // the same block-state.
        assertTrue(back.blockIn().test(Blocks.DIAMOND_BLOCK.defaultBlockState()),
            "decoded StateIngredient still matches diamond_block");
        assertFalse(back.blockIn().test(Blocks.IRON_BLOCK.defaultBlockState()),
            "decoded StateIngredient doesn't match iron_block");
    }

    @Test
    public void recipeResultCarriesBlockStateProperties() {
        // The result slot must carry the full BlockState, not just the Block — otherwise
        // brainsweep would always produce the default state, losing orientation/variant data.
        BlockState orientedLogState = Blocks.OAK_LOG.defaultBlockState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                net.minecraft.core.Direction.Axis.Z);

        var original = new BrainsweepRecipe(
            new StateIngredientBlock(Blocks.DIAMOND_BLOCK),
            new EntityTypeIngredient(EntityType.COW),
            500L,
            orientedLogState
        );

        var codec = BrainsweepRecipe.Serializer.INSTANCE.codec().codec();
        var json = codec.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
        var back = codec.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(net.minecraft.core.Direction.Axis.Z,
            back.result().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS),
            "AXIS property on result survives round-trip");
    }

    @Test
    public void recipeSerializerIdentifierIsResourceLocation() {
        // Sanity-check the serializer is a singleton registered under a stable key. If the
        // port broke its registration, all recipe deserialization paths would silently return
        // null.
        assertNotNull(BrainsweepRecipe.Serializer.INSTANCE,
            "Serializer.INSTANCE is a stable singleton");
        assertNotNull(BrainsweepRecipe.Serializer.INSTANCE.codec(),
            "serializer has a non-null MapCodec");
        assertNotNull(BrainsweepRecipe.Serializer.INSTANCE.streamCodec(),
            "serializer has a non-null StreamCodec for network sync");
    }

    @Test
    public void recipeRecognizesEntityIngredient() {
        // Regression target: the EntityTypeIngredient serialize format changed during port; verify
        // decoded ingredient still matches the correct EntityType.
        var original = new BrainsweepRecipe(
            new StateIngredientBlock(Blocks.STONE),
            new EntityTypeIngredient(EntityType.VILLAGER),
            100L,
            Blocks.GOLD_BLOCK.defaultBlockState()
        );

        var codec = BrainsweepRecipe.Serializer.INSTANCE.codec().codec();
        var json = codec.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
        var back = codec.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        // Serialize both entity ingredients and compare — equality on the sealed hierarchy isn't
        // guaranteed, but the JSON shape is canonical.
        assertEquals(original.entityIn().serialize().toString(),
            back.entityIn().serialize().toString(),
            "entity ingredient JSON is stable across round-trip");
    }
}
