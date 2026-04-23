package at.petrak.hexcasting.forge.recipe;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

/**
 * 1.21: custom ingredients live on {@link ICustomIngredient} with codec/streamCodec, not
 * the old {@code AbstractIngredient} + {@code IIngredientSerializer}. The IngredientType
 * itself is registered via DeferredRegister in the platform init.
 */
public class ForgeUnsealedIngredient implements ICustomIngredient {
    public static final ResourceLocation ID = modLoc("unsealed");

    public static final MapCodec<ForgeUnsealedIngredient> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(ForgeUnsealedIngredient::getStack)
        ).apply(inst, ForgeUnsealedIngredient::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeUnsealedIngredient> STREAM_CODEC =
        StreamCodec.composite(
            ItemStack.STREAM_CODEC, ForgeUnsealedIngredient::getStack,
            ForgeUnsealedIngredient::new
        );

    // The IngredientType is wired from the platform init (DeferredRegister<IngredientType<?>>).
    // Until that's ported, getType() returns null — the type is only consulted on recipe
    // encode, which doesn't happen before the registry is populated.
    public static IngredientType<ForgeUnsealedIngredient> TYPE;

    private final ItemStack stack;

    private static ItemStack createStack(ItemStack base) {
        ItemStack newStack = base.copy();
        NBTHelper.putString(newStack, IotaHolderItem.TAG_OVERRIDE_VISUALLY, "any");
        return newStack;
    }

    public ForgeUnsealedIngredient(ItemStack stack) {
        this.stack = stack;
    }

    public static ForgeUnsealedIngredient of(ItemStack stack) {
        return new ForgeUnsealedIngredient(stack);
    }

    public ItemStack getStack() {
        return this.stack;
    }

    @Override
    public boolean test(ItemStack input) {
        if (input == null) {
            return false;
        }
        if (this.stack.getItem() == input.getItem() && this.stack.getDamageValue() == input.getDamageValue()) {
            ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(this.stack);
            if (holder != null) {
                return holder.readIotaTag() != null && holder.writeIota(new NullIota(), true);
            }
        }
        return false;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(createStack(this.stack));
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }
}
