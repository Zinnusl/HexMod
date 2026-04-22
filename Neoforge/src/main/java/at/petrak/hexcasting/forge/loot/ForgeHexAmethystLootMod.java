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
        // 1.21: LootContext exposes the level's reloadable registries via getResolver();
        // loot tables are looked up with a ResourceKey<LootTable>.
        var tableKey = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.LOOT_TABLE,
            HexLootHandler.TABLE_INJECT_AMETHYST_CLUSTER);
        var injectPool = context.getResolver().lookupOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE)
            .getOrThrow(tableKey).value();
        injectPool.getRandomItemsRaw(context, generatedLoot::add);

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
