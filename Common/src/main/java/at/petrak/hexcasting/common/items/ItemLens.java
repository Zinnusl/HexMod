package at.petrak.hexcasting.common.items;

import at.petrak.hexcasting.annotations.SoftImplement;
import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemLens extends Item implements HexBaubleItem {
    // 1.21: AttributeModifier uses a ResourceLocation id instead of the old UUID + name pair;
    // amount is still additive/multiplicative based on Operation.
    public static final AttributeModifier GRID_ZOOM = new AttributeModifier(
        ResourceLocation.fromNamespaceAndPath(HexAPI.MOD_ID, "lens_grid_zoom"),
        0.33, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    public static final AttributeModifier SCRY_SIGHT = new AttributeModifier(
        ResourceLocation.fromNamespaceAndPath(HexAPI.MOD_ID, "lens_scry_sight"),
        1.0, AttributeModifier.Operation.ADD_VALUE);

    public ItemLens(Properties pProperties) {
        super(pProperties);
        DispenserBlock.registerBehavior(this, new OptionalDispenseItemBehavior() {
            @Override
            protected @NotNull ItemStack execute(@NotNull BlockSource world, @NotNull ItemStack stack) {
                this.setSuccess(ArmorItem.dispenseArmor(world, stack));
                return stack;
            }
        });
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getHexBaubleAttrs(ItemStack stack) {
        HashMultimap<Holder<Attribute>, AttributeModifier> out = HashMultimap.create();
        out.put(HexAttributes.holder(HexAttributes.GRID_ZOOM), GRID_ZOOM);
        out.put(HexAttributes.holder(HexAttributes.SCRY_SIGHT), SCRY_SIGHT);
        return out;
    }

    // In fabric impled with extension property?
    @Nullable
    @SoftImplement("forge")
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }
}
