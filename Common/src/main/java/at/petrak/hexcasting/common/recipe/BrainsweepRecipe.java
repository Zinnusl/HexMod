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
     * TODO(port-1.21): rewrite serializer on codec/streamCodec API. Current stub keeps
     * the type system happy but will throw if the game actually tries to load or sync a
     * brainsweep recipe.
     */
    public static class Serializer implements RecipeSerializer<BrainsweepRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public MapCodec<BrainsweepRecipe> codec() {
            throw new UnsupportedOperationException("BrainsweepRecipe codec not ported to 1.21 yet");
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BrainsweepRecipe> streamCodec() {
            throw new UnsupportedOperationException("BrainsweepRecipe streamCodec not ported to 1.21 yet");
        }
    }
}
