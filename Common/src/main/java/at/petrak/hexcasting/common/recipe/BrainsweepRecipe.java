package at.petrak.hexcasting.common.recipe;

import at.petrak.hexcasting.common.recipe.ingredient.StateIngredient;
import at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;

/**
 * 1.21: Recipe<Container> → Recipe<RecipeInput>, getId removed, assemble/getResultItem
 * take HolderLookup.Provider, RecipeSerializer is codec-based. We only use the custom
 * {@link #matches(BlockState, Entity, ServerLevel)} overload so the Recipe interface
 * methods are pro-forma no-ops. Codec serialization has not been ported yet.
 */
public record BrainsweepRecipe(
    StateIngredient blockIn,
    BrainsweepeeIngredient entityIn,
    long mediaCost,
    BlockState result
) implements Recipe<BrainsweepRecipe.Input> {
    public boolean matches(BlockState blockIn, Entity victim, ServerLevel level) {
        return this.blockIn.test(blockIn) && this.entityIn.test(victim, level);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider provider) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return ItemStack.EMPTY.copy();
    }

    @Override
    public RecipeType<?> getType() {
        return HexRecipeStuffRegistry.BRAINSWEEP_TYPE;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexRecipeStuffRegistry.BRAINSWEEP;
    }

    // Because kotlin doesn't like doing raw, unchecked types
    // Can't blame it, but that's what we need to do
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static BlockState copyProperties(BlockState original, BlockState copyTo) {
        for (Property prop : original.getProperties()) {
            if (copyTo.hasProperty(prop)) {
                copyTo = copyTo.setValue(prop, original.getValue(prop));
            }
        }
        return copyTo;
    }

    /** Empty recipe input — brainsweep doesn't consume any ItemStack. */
    public record Input() implements RecipeInput {
        @Override public @NotNull ItemStack getItem(int slot) { return ItemStack.EMPTY; }
        @Override public int size() { return 0; }
    }

    /**
     * 1.21 interim serializer: the real StateIngredient / BrainsweepeeIngredient codecs
     * haven't been ported yet, so we parse the raw JSON as a tolerant wrapper producing a
     * brainsweep that never matches. That keeps existing {@code hexcasting:brainsweep}
     * recipe JSONs loadable on world join — they just won't fire until the real codec
     * lands. Network sync is stubbed in the same spirit.
     */
    public static class Serializer implements RecipeSerializer<BrainsweepRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final BrainsweepRecipe DUMMY = new BrainsweepRecipe(
            new StubStateIngredient(),
            new StubBrainsweepeeIngredient(),
            0L,
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
        );

        private static final MapCodec<BrainsweepRecipe> CODEC = MapCodec.unit(DUMMY);

        private static final StreamCodec<RegistryFriendlyByteBuf, BrainsweepRecipe> STREAM_CODEC =
            StreamCodec.unit(DUMMY);

        @Override
        public MapCodec<BrainsweepRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BrainsweepRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    private static final class StubStateIngredient implements StateIngredient {
        @Override public boolean test(BlockState state) { return false; }
        @Override public BlockState pick(java.util.Random random) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        @Override public com.google.gson.JsonObject serialize() { return new com.google.gson.JsonObject(); }
        @Override public void write(net.minecraft.network.FriendlyByteBuf buffer) { }
        @Override public java.util.List<ItemStack> getDisplayedStacks() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<BlockState> getDisplayed() { return java.util.Collections.emptyList(); }
    }

    private static final class StubBrainsweepeeIngredient extends at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient {
        @Override public boolean test(net.minecraft.world.entity.Entity entity, ServerLevel level) { return false; }
        @Override public net.minecraft.network.chat.Component getName() { return net.minecraft.network.chat.Component.empty(); }
        @Override public java.util.List<net.minecraft.network.chat.Component> getTooltip(boolean advanced) { return java.util.Collections.emptyList(); }
        @Override public com.google.gson.JsonObject serialize() { return new com.google.gson.JsonObject(); }
        @Override public void write(net.minecraft.network.FriendlyByteBuf buf) { }
        @Override public net.minecraft.world.entity.Entity exampleEntity(net.minecraft.world.level.Level level) { return null; }
        @Override public at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient.Type ingrType() {
            return at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient.Type.ENTITY_TYPE;
        }
        @Override public String getSomeKindOfReasonableIDForEmi() { return "hexcasting:stub"; }
    }
}
