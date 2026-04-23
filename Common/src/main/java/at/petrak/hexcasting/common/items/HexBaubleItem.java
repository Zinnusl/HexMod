package at.petrak.hexcasting.common.items;

import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21: Attribute modifier APIs take Holder<Attribute>, not the bare Attribute.
 */
public interface HexBaubleItem {
    Multimap<Holder<Attribute>, AttributeModifier> getHexBaubleAttrs(ItemStack stack);
}
