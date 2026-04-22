package at.petrak.hexcasting.common.items;

import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ItemJewelerHammer extends PickaxeItem {
    // 1.21: PickaxeItem takes (Tier, Properties); attribute modifiers live on
    // Properties.attributes() via the Tool/AttributeModifiers data component.
    public ItemJewelerHammer(Tier tier, int damageMod, float attackSpeedMod, Properties props) {
        super(tier, props.attributes(PickaxeItem.createAttributes(tier, damageMod, attackSpeedMod)));
    }

    public static boolean shouldFailToBreak(Player player, BlockState state, BlockPos pos) {
        ItemStack stack = player.getMainHandItem();
        return stack.is(HexItems.JEWELER_HAMMER) && Block.isShapeFullBlock(state.getShape(player.level(), pos));
    }
}
