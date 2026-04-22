package at.petrak.hexcasting.common.items.pigment;

import at.petrak.hexcasting.api.addldata.ADPigment;
import at.petrak.hexcasting.api.item.PigmentItem;
import at.petrak.hexcasting.api.pigment.ColorProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Random;
import java.util.UUID;

public class ItemUUIDPigment extends Item implements PigmentItem {
    public ItemUUIDPigment(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public ColorProvider provideColor(ItemStack stack, UUID owner) {
        return new MyColorProvider(owner);
    }

    protected static class MyColorProvider extends ColorProvider {
        private final int[] colors;

        MyColorProvider(UUID owner) {
            // TODO(port-1.21): PaucalAPI#getContributor was removed; restore the
            // contributor-lookup custom colour path once paucal 0.7.x exposes an
            // equivalent. Fall through to the deterministic-scramble path below.

            // randomly scrungle the bits
            var rand = new Random(owner.getLeastSignificantBits() ^ owner.getMostSignificantBits());
            var hue1 = rand.nextFloat();
            var saturation1 = rand.nextFloat(0.4f, 0.8f);
            var brightness1 = rand.nextFloat(0.7f, 1.0f);
            var hue2 = rand.nextFloat();
            var saturation2 = rand.nextFloat(0.7f, 1.0f);
            var brightness2 = rand.nextFloat(0.2f, 0.7f);

            var col1 = Color.HSBtoRGB(hue1, saturation1, brightness1);
            var col2 = Color.HSBtoRGB(hue2, saturation2, brightness2);
            this.colors = new int[]{col1, col2};
        }


        @Override
        protected int getRawColor(float time, Vec3 position) {
            return ADPigment.morphBetweenColors(this.colors, new Vec3(0.1, 0.1, 0.1), time / 400, position);
        }
    }
}
