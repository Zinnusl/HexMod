package at.petrak.hexcasting.common.items.armor;

import at.petrak.hexcasting.api.HexAPI;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;

/**
 * To get the armor model in;
 * On forge: cursed self-mixin
 * On fabric: hook in ClientInit
 * <p>
 * 1.21: ArmorItem takes Holder&lt;ArmorMaterial&gt; rather than the raw material.
 */
public class ItemRobes extends ArmorItem {
    public final ArmorItem.Type type;

    public ItemRobes(ArmorItem.Type type, Properties properties) {
        super(Holder.direct(HexAPI.instance().robesMaterial()), type, properties);
        this.type = type;
    }
}
