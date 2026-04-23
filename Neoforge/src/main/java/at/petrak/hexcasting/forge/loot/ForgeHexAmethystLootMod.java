package at.petrak.hexcasting.forge.loot;

import at.petrak.hexcasting.common.loot.AmethystReducerFunc;
import at.petrak.hexcasting.common.loot.HexLootHandler;
import at.petrak.hexcasting.forge.lib.ForgeHexLootMods;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;


public class ForgeHexAmethystLootMod extends LootModifier {
    public static final MapCodec<ForgeHexAmethystLootMod> CODEC = RecordCodecBuilder.mapCodec(inst ->
        codecStart(inst).and(
            Codec.DOUBLE.fieldOf("shardDelta").forGetter((ForgeHexAmethystLootMod it) -> it.shardDelta)
        ).apply(inst, ForgeHexAmethystLootMod::new)
    );

    public final double shardDelta;

    public ForgeHexAmethystLootMod(LootItemCondition[] conditionsIn, double shardDelta) {
        super(conditionsIn);
        this.shardDelta = shardDelta;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
        LootContext context) {
        // 1.21: LootContext.getResolver().lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(key)
        // throws if the inject table isn't present in the current registry (e.g. pack
        // validation race on world first-load, or a malformed JSON). Previously the
        // uncaught throw propagated out to the block-break handler and took the
        // integrated server down, which hard-crashed the client mid-loot-roll. Fail
        // soft: log once, skip the extra drops, still apply the shard reducer.
        try {
            var tableKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                HexLootHandler.TABLE_INJECT_AMETHYST_CLUSTER);
            var injectLookup = context.getResolver()
                .lookup(net.minecraft.core.registries.Registries.LOOT_TABLE);
            if (injectLookup.isPresent()) {
                var holder = injectLookup.get().get(tableKey);
                if (holder.isPresent()) {
                    holder.get().value().getRandomItemsRaw(context, generatedLoot::add);
                }
            }
        } catch (Throwable t) {
            at.petrak.hexcasting.api.HexAPI.LOGGER.error(
                "amethyst cluster inject table {} unresolved; skipping extra drops",
                HexLootHandler.TABLE_INJECT_AMETHYST_CLUSTER, t);
        }

        for (var stack : generatedLoot) {
            AmethystReducerFunc.doStatic(stack, context, this.shardDelta);
        }

        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ForgeHexLootMods.AMETHYST.get();
    }
}
