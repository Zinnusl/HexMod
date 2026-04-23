package at.petrak.hexcasting.common.lib;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

public class HexPotions {
    public static void register(BiConsumer<Potion, ResourceLocation> r) {
        for (var e : POTIONS.entrySet()) {
            r.accept(e.getValue(), e.getKey());
        }
    }

    private static final Map<ResourceLocation, Potion> POTIONS = new LinkedHashMap<>();

    // 1.21: MobEffectInstance takes Holder<MobEffect>. Effects aren't yet registered when
    // these constants initialize, so wrap with Holder.direct — they'll still resolve
    // through the registered-name path at runtime because MobEffect equality is identity.
    public static final Potion ENLARGE_GRID = make("enlarge_grid",
        new Potion("enlarge_grid", new MobEffectInstance(Holder.direct(HexMobEffects.ENLARGE_GRID), 3600)));
    public static final Potion ENLARGE_GRID_LONG = make("enlarge_grid_long",
        new Potion("enlarge_grid_long", new MobEffectInstance(Holder.direct(HexMobEffects.ENLARGE_GRID), 9600)));
    public static final Potion ENLARGE_GRID_STRONG = make("enlarge_grid_strong",
        new Potion("enlarge_grid_strong", new MobEffectInstance(Holder.direct(HexMobEffects.ENLARGE_GRID), 1800, 1)));

    public static final Potion SHRINK_GRID = make("shrink_grid",
        new Potion("shrink_grid", new MobEffectInstance(Holder.direct(HexMobEffects.SHRINK_GRID), 3600)));
    public static final Potion SHRINK_GRID_LONG = make("shrink_grid_long",
        new Potion("shrink_grid_long", new MobEffectInstance(Holder.direct(HexMobEffects.SHRINK_GRID), 9600)));
    public static final Potion SHRINK_GRID_STRONG = make("shrink_grid_strong",
        new Potion("shrink_grid_strong", new MobEffectInstance(Holder.direct(HexMobEffects.SHRINK_GRID), 1800, 1)));

    /**
     * 1.21: brewing-recipe registration lives on the PotionBrewing.Builder fed by
     * RegisterBrewingRecipesEvent (NeoForge) or the FabricBrewingRecipeRegistryBuilder
     * event (Fabric). Platforms call this with their builder + the event's RegistryAccess.
     */
    public static void registerMixes(PotionBrewing.Builder builder, HolderLookup.Provider registryAccess) {
        HolderLookup.RegistryLookup<Potion> lookup = registryAccess.lookupOrThrow(Registries.POTION);
        Holder<Potion> enlarge = lookup.getOrThrow(ResourceKey.create(Registries.POTION, modLoc("enlarge_grid")));
        Holder<Potion> enlargeLong = lookup.getOrThrow(ResourceKey.create(Registries.POTION, modLoc("enlarge_grid_long")));
        Holder<Potion> enlargeStrong = lookup.getOrThrow(ResourceKey.create(Registries.POTION, modLoc("enlarge_grid_strong")));
        Holder<Potion> shrink = lookup.getOrThrow(ResourceKey.create(Registries.POTION, modLoc("shrink_grid")));
        Holder<Potion> shrinkLong = lookup.getOrThrow(ResourceKey.create(Registries.POTION, modLoc("shrink_grid_long")));
        Holder<Potion> shrinkStrong = lookup.getOrThrow(ResourceKey.create(Registries.POTION, modLoc("shrink_grid_strong")));

        builder.addMix(Potions.AWKWARD, HexItems.AMETHYST_DUST, enlarge);
        builder.addMix(enlarge, Items.REDSTONE, enlargeLong);
        builder.addMix(enlarge, Items.GLOWSTONE_DUST, enlargeStrong);

        builder.addMix(enlarge, Items.FERMENTED_SPIDER_EYE, shrink);
        builder.addMix(enlargeLong, Items.FERMENTED_SPIDER_EYE, shrinkLong);
        builder.addMix(enlargeStrong, Items.FERMENTED_SPIDER_EYE, shrinkStrong);

        builder.addMix(shrink, Items.REDSTONE, shrinkLong);
        builder.addMix(shrink, Items.GLOWSTONE_DUST, shrinkStrong);
    }

    private static <T extends Potion> T make(String id, T potion) {
        var old = POTIONS.put(modLoc(id), potion);
        if (old != null) {
            throw new IllegalArgumentException("Typo? Duplicate id " + id);
        }
        return potion;
    }
}
