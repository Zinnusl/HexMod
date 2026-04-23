package at.petrak.hexcasting.forge.recipe;

import at.petrak.hexcasting.api.HexAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 1.21 NeoForge: custom ingredient codecs live in
 * {@link NeoForgeRegistries#INGREDIENT_TYPES}. Hex has two — unsealed (a stack-carrier
 * used for brainsweep inputs) and mod_conditional (a dispatch between a default
 * ingredient and an alternative present only when a specific mod is loaded).
 */
public final class ForgeHexIngredients {
    private ForgeHexIngredients() {}

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, HexAPI.MOD_ID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<ForgeUnsealedIngredient>> UNSEALED =
        INGREDIENT_TYPES.register("unsealed",
            () -> new IngredientType<>(ForgeUnsealedIngredient.CODEC, ForgeUnsealedIngredient.STREAM_CODEC));

    public static final DeferredHolder<IngredientType<?>, IngredientType<ForgeModConditionalIngredient>> MOD_CONDITIONAL =
        INGREDIENT_TYPES.register("mod_conditional",
            () -> new IngredientType<>(ForgeModConditionalIngredient.CODEC, ForgeModConditionalIngredient.STREAM_CODEC));

    /** Called during {@code initRegistries} so the DR's mod-bus listeners fire. */
    public static void bootstrap(IEventBus modBus) {
        INGREDIENT_TYPES.register(modBus);
        // Populate the static TYPE fields once the registry is built so getType() can
        // resolve during recipe encode. We use the DeferredHolder that's now resolved.
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent evt) -> evt.enqueueWork(() -> {
            ForgeUnsealedIngredient.TYPE = UNSEALED.get();
            ForgeModConditionalIngredient.TYPE = MOD_CONDITIONAL.get();
        }));
    }
}
