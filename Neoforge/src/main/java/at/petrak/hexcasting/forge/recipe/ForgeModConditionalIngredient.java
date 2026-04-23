package at.petrak.hexcasting.forge.recipe;

import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

/**
 * 1.21: custom ingredients are ICustomIngredient records with codec/streamCodec. The
 * old JSON toJson / IIngredientSerializer pair is gone.
 */
public class ForgeModConditionalIngredient implements ICustomIngredient {
    public static final ResourceLocation ID = modLoc("mod_conditional");

    // Tolerant variant of Ingredient.CODEC: if the inner ingredient references an item
    // that doesn't exist at parse time (e.g. farmersdelight:skillet while FD is absent),
    // silently substitute Ingredient.of(). That's the whole point of mod_conditional —
    // letting a recipe reference a mod-specific item without failing to parse when the
    // mod is missing. Vanilla Ingredient.CODEC is eager, so wrap it.
    private static final com.mojang.serialization.Codec<Ingredient> TOLERANT_INGREDIENT =
        com.mojang.serialization.Codec.PASSTHROUGH.flatXmap(
            dyn -> com.mojang.serialization.DataResult.success(
                Ingredient.CODEC.parse(dyn).result().orElse(Ingredient.of())),
            ing -> {
                var r = Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ing);
                return r.map(json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json));
            }
        );

    public static final MapCodec<ForgeModConditionalIngredient> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            TOLERANT_INGREDIENT.fieldOf("default").forGetter(ForgeModConditionalIngredient::getMain),
            com.mojang.serialization.Codec.STRING.fieldOf("modid").forGetter(ForgeModConditionalIngredient::getModid),
            TOLERANT_INGREDIENT.fieldOf("if_loaded").forGetter(ForgeModConditionalIngredient::getIfModLoaded)
        ).apply(inst, ForgeModConditionalIngredient::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeModConditionalIngredient> STREAM_CODEC =
        StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, ForgeModConditionalIngredient::getMain,
            ByteBufCodecs.STRING_UTF8, ForgeModConditionalIngredient::getModid,
            Ingredient.CONTENTS_STREAM_CODEC, ForgeModConditionalIngredient::getIfModLoaded,
            ForgeModConditionalIngredient::new
        );

    public static IngredientType<ForgeModConditionalIngredient> TYPE;

    private final Ingredient main;
    private final String modid;
    private final Ingredient ifModLoaded;
    private final Ingredient toUse;

    public ForgeModConditionalIngredient(Ingredient main, String modid, Ingredient ifModLoaded) {
        this.main = main;
        this.modid = modid;
        this.ifModLoaded = ifModLoaded;
        this.toUse = IXplatAbstractions.INSTANCE.isModPresent(modid) ? ifModLoaded : main;
    }

    public static ForgeModConditionalIngredient of(Ingredient main, String modid, Ingredient ifModLoaded) {
        return new ForgeModConditionalIngredient(main, modid, ifModLoaded);
    }

    public Ingredient getMain() {
        return this.main;
    }

    public String getModid() {
        return this.modid;
    }

    public Ingredient getIfModLoaded() {
        return this.ifModLoaded;
    }

    @Override
    public boolean test(ItemStack input) {
        return this.toUse.test(input);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(this.toUse.getItems());
    }

    @Override
    public boolean isSimple() {
        return this.toUse.isSimple();
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }
}
