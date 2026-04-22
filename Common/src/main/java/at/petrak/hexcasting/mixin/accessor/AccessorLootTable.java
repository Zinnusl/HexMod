package at.petrak.hexcasting.mixin.accessor;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import java.util.function.BiFunction;

/**
 * TODO(port-1.21): LootTable's internal function array moved behind codecs. A stub
 * interface is retained here (no @Mixin annotation — the original targeted a non-interface
 * class and 1.21 closed off those fields anyway) so Fabric-side callers that cast to this
 * type still compile. The amethyst-loot patch must be reimplemented on top of the new
 * loot-table modifier API before it does anything at runtime.
 */
public interface AccessorLootTable {
    default LootItemFunction[] hex$getFunctions() {
        return new LootItemFunction[0];
    }

    default void hex$setFunctions(LootItemFunction[] lifs) {
    }

    default void hex$setCompositeFunction(BiFunction<ItemStack, LootContext, ItemStack> bf) {
    }
}
