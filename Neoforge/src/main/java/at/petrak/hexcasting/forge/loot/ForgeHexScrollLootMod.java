package at.petrak.hexcasting.forge.loot;

import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.loot.AddPerWorldPatternToScrollFunc;
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


/**
 * 1.21 NeoForge: LootModifier constructor takes {@code List<LootItemCondition>}, and
 * codec() returns a {@link MapCodec}.
 */
public class ForgeHexScrollLootMod extends LootModifier {
    public static final MapCodec<ForgeHexScrollLootMod> CODEC = RecordCodecBuilder.mapCodec(inst ->
        codecStart(inst).and(
            Codec.INT.fieldOf("countRange").forGetter((ForgeHexScrollLootMod it) -> it.countRange)
        ).apply(inst, ForgeHexScrollLootMod::new)
    );

    public final int countRange;

    public ForgeHexScrollLootMod(LootItemCondition[] conditionsIn, int countRange) {
        super(conditionsIn);
        this.countRange = countRange;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
        LootContext context) {
        var count = this.countRange < 0 ? 1 : HexLootHandler.getScrollCount(this.countRange, context.getRandom());
        for (int i = 0; i < count; i++) {
            var newStack = new ItemStack(HexItems.SCROLL_LARGE);
            AddPerWorldPatternToScrollFunc.doStatic(newStack, context.getRandom(), context.getLevel().getServer().overworld());
            generatedLoot.add(newStack);
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ForgeHexLootMods.INJECT_SCROLLS.get();
    }
}
