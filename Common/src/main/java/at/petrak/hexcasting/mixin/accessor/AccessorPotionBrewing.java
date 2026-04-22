package at.petrak.hexcasting.mixin.accessor;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/**
 * TODO(port-1.21): 1.21 PotionBrewing uses a builder + {@code RegisterBrewingRecipesEvent}
 * on Neoforge. The old static {@code addMix} entry point is gone. This stub satisfies
 * {@link at.petrak.hexcasting.common.lib.HexPotions} until the platform init wires up
 * the new event; calls are no-ops.
 */
public class AccessorPotionBrewing {
    public static void addMix(Potion input, Item reagent, Potion output) {
    }
}
