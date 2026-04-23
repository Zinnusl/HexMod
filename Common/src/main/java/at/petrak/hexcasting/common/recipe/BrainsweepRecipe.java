package at.petrak.hexcasting.common.recipe;

import at.petrak.hexcasting.common.recipe.ingredient.StateIngredient;
import at.petrak.hexcasting.common.recipe.ingredient.StateIngredientHelper;
import at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
     * Round-trip Codec<StateIngredient> that goes via JsonOps and delegates to
     * StateIngredientHelper.deserialize / StateIngredient.serialize. Avoids duplicating
     * the full sealed-subclass codec tree on the new API.
     */
    public static final Codec<StateIngredient> STATE_INGREDIENT_CODEC =
        Codec.PASSTHROUGH.flatXmap(
            dyn -> {
                try {
                    JsonElement json = dyn.convert(JsonOps.INSTANCE).getValue();
                    return DataResult.success(StateIngredientHelper.deserialize(json.getAsJsonObject()));
                } catch (RuntimeException e) {
                    return DataResult.error(e::getMessage);
                }
            },
            ingr -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, ingr.serialize()))
        );

    public static final Codec<BrainsweepeeIngredient> BRAINSWEEPEE_INGREDIENT_CODEC =
        Codec.PASSTHROUGH.flatXmap(
            dyn -> {
                try {
                    JsonElement json = dyn.convert(JsonOps.INSTANCE).getValue();
                    return DataResult.success(BrainsweepeeIngredient.deserialize(json.getAsJsonObject()));
                } catch (RuntimeException e) {
                    return DataResult.error(e::getMessage);
                }
            },
            ingr -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, ingr.serialize()))
        );

    /** BlockState codec that matches the {@code {name: "...", properties: {...}}} shape of the recipe JSON. */
    public static final Codec<BlockState> RESULT_BLOCK_STATE_CODEC = Codec.PASSTHROUGH.flatXmap(
        dyn -> {
            try {
                JsonObject json = dyn.convert(JsonOps.INSTANCE).getValue().getAsJsonObject();
                return DataResult.success(StateIngredientHelper.readBlockState(json));
            } catch (RuntimeException e) {
                return DataResult.error(e::getMessage);
            }
        },
        state -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, StateIngredientHelper.serializeBlockState(state)))
    );

    public static class Serializer implements RecipeSerializer<BrainsweepRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<BrainsweepRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            STATE_INGREDIENT_CODEC.fieldOf("blockIn").forGetter(BrainsweepRecipe::blockIn),
            BRAINSWEEPEE_INGREDIENT_CODEC.fieldOf("entityIn").forGetter(BrainsweepRecipe::entityIn),
            Codec.LONG.fieldOf("cost").forGetter(BrainsweepRecipe::mediaCost),
            RESULT_BLOCK_STATE_CODEC.fieldOf("result").forGetter(BrainsweepRecipe::result)
        ).apply(inst, BrainsweepRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, BrainsweepRecipe> STREAM_CODEC =
            StreamCodec.of(
                (buf, recipe) -> {
                    // Reuse the JSON roundtrip for state + entity ingredients; primitive scalars go direct.
                    ByteBufCodecs.STRING_UTF8.encode(buf, recipe.blockIn.serialize().toString());
                    recipe.entityIn.wrapWrite(buf);
                    buf.writeLong(recipe.mediaCost);
                    // BlockState fits through the canonical id encoding.
                    buf.writeVarInt(net.minecraft.world.level.block.Block.getId(recipe.result));
                },
                buf -> {
                    String blockJson = ByteBufCodecs.STRING_UTF8.decode(buf);
                    StateIngredient block = StateIngredientHelper.deserialize(
                        com.google.gson.JsonParser.parseString(blockJson).getAsJsonObject());
                    BrainsweepeeIngredient entity = BrainsweepeeIngredient.read(buf);
                    long cost = buf.readLong();
                    BlockState result = net.minecraft.world.level.block.Block.stateById(buf.readVarInt());
                    return new BrainsweepRecipe(block, entity, cost, result);
                }
            );

        @Override
        public MapCodec<BrainsweepRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BrainsweepRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
