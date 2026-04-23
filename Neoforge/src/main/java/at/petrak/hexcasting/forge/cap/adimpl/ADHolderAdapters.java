package at.petrak.hexcasting.forge.cap.adimpl;

import at.petrak.hexcasting.api.addldata.ADHexHolder;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.addldata.ADVariantItem;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.item.HexHolderItem;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.item.MediaHolderItem;
import at.petrak.hexcasting.api.item.VariantItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Stack-scoped AD holder adapters. Mirrors the Fabric {@code CC*} wrappers but instance-
 * dispatched: each factory checks {@link ItemStack#getItem()} against the known holder
 * interfaces and returns a fresh wrapper. No Capability/AttachmentType registration is
 * needed — these are ephemeral views constructed per lookup.
 */
public final class ADHolderAdapters {
    private ADHolderAdapters() {}

    public static @Nullable ADMediaHolder media(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var item = stack.getItem();

        if (item instanceof MediaHolderItem mhi) {
            return new ItemMediaHolder(stack, mhi);
        }
        if (item == HexItems.AMETHYST_DUST) {
            return new StaticMediaHolder(stack, () -> HexConfig.common().dustMediaAmount(),
                ADMediaHolder.AMETHYST_DUST_PRIORITY);
        }
        if (item == Items.AMETHYST_SHARD) {
            return new StaticMediaHolder(stack, () -> HexConfig.common().shardMediaAmount(),
                ADMediaHolder.AMETHYST_SHARD_PRIORITY);
        }
        if (item == HexItems.CHARGED_AMETHYST) {
            return new StaticMediaHolder(stack, () -> HexConfig.common().chargedCrystalMediaAmount(),
                ADMediaHolder.CHARGED_AMETHYST_PRIORITY);
        }
        if (item == HexItems.QUENCHED_SHARD.asItem()) {
            return new StaticMediaHolder(stack, () -> MediaConstants.QUENCHED_SHARD_UNIT,
                ADMediaHolder.QUENCHED_SHARD_PRIORITY);
        }
        if (item == HexBlocks.QUENCHED_ALLAY.asItem()) {
            return new StaticMediaHolder(stack, () -> MediaConstants.QUENCHED_BLOCK_UNIT,
                ADMediaHolder.QUENCHED_ALLAY_PRIORITY);
        }
        return null;
    }

    public static @Nullable ADIotaHolder iota(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var item = stack.getItem();
        if (item instanceof IotaHolderItem ihi) {
            return new ItemIotaHolder(stack, ihi);
        }
        if (item == Items.PUMPKIN_PIE) {
            int count = stack.getCount();
            return new ADIotaHolder() {
                @Override public @Nullable CompoundTag readIotaTag() { return null; }
                @Override public @Nullable Iota readIota(ServerLevel level) { return new DoubleIota(Math.PI * count); }
                @Override public boolean writeable() { return false; }
                @Override public boolean writeIota(@Nullable Iota iota, boolean simulate) { return false; }
                @Override public @Nullable Iota emptyIota() { return new DoubleIota(0); }
            };
        }
        return null;
    }

    public static @Nullable ADHexHolder hex(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof HexHolderItem hhi) {
            return new ItemHexHolder(stack, hhi);
        }
        return null;
    }

    public static @Nullable ADVariantItem variant(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof VariantItem vi) {
            return new ItemVariantHolder(stack, vi);
        }
        return null;
    }

    // ---- concrete adapters -------------------------------------------------

    private record ItemMediaHolder(ItemStack stack, MediaHolderItem item) implements ADMediaHolder {
        @Override public long getMedia() { return item.getMedia(stack); }
        @Override public long getMaxMedia() { return item.getMaxMedia(stack); }
        @Override public void setMedia(long media) { item.setMedia(stack, media); }
        @Override public boolean canRecharge() { return item.canRecharge(stack); }
        @Override public boolean canProvide() { return item.canProvideMedia(stack); }
        @Override public int getConsumptionPriority() { return item.getConsumptionPriority(stack); }
        @Override public boolean canConstructBattery() { return false; }
        @Override public long withdrawMedia(long cost, boolean simulate) { return item.withdrawMedia(stack, cost, simulate); }
        @Override public long insertMedia(long amount, boolean simulate) { return item.insertMedia(stack, amount, simulate); }
    }

    private record StaticMediaHolder(ItemStack stack, LongSupplier baseWorth, int consumptionPriority) implements ADMediaHolder {
        @Override public long getMedia() { return baseWorth.getAsLong() * stack.getCount(); }
        @Override public long getMaxMedia() { return getMedia(); }
        @Override public void setMedia(long media) { }
        @Override public boolean canRecharge() { return false; }
        @Override public boolean canProvide() { return true; }
        @Override public int getConsumptionPriority() { return consumptionPriority; }
        @Override public boolean canConstructBattery() { return true; }
        @Override public long withdrawMedia(long cost, boolean simulate) {
            long worth = baseWorth.getAsLong();
            if (cost < 0) {
                cost = worth * stack.getCount();
            }
            double itemsRequired = cost / (double) worth;
            int itemsUsed = Math.min((int) Math.ceil(itemsRequired), stack.getCount());
            if (!simulate) {
                stack.shrink(itemsUsed);
            }
            return itemsUsed * worth;
        }
    }

    private record ItemIotaHolder(ItemStack stack, IotaHolderItem item) implements ADIotaHolder {
        @Override public @Nullable CompoundTag readIotaTag() { return item.readIotaTag(stack); }
        @Override public @Nullable Iota readIota(ServerLevel level) { return item.readIota(stack, level); }
        @Override public @Nullable Iota emptyIota() { return item.emptyIota(stack); }
        @Override public boolean writeable() { return item.writeable(stack); }
        @Override public boolean writeIota(@Nullable Iota iota, boolean simulate) {
            if (!item.canWrite(stack, iota)) return false;
            if (!simulate) item.writeDatum(stack, iota);
            return true;
        }
    }

    private record ItemHexHolder(ItemStack stack, HexHolderItem item) implements ADHexHolder {
        @Override public boolean canDrawMediaFromInventory() { return item.canDrawMediaFromInventory(stack); }
        @Override public boolean hasHex() { return item.hasHex(stack); }
        @Override public @Nullable List<Iota> getHex(ServerLevel level) { return item.getHex(stack, level); }
        @Override public void writeHex(List<Iota> patterns, @Nullable FrozenPigment pigment, long media) {
            item.writeHex(stack, patterns, pigment, media);
        }
        @Override public void clearHex() { item.clearHex(stack); }
        @Override public @Nullable FrozenPigment getPigment() { return item.getPigment(stack); }
    }

    private record ItemVariantHolder(ItemStack stack, VariantItem item) implements ADVariantItem {
        @Override public int numVariants() { return item.numVariants(); }
        @Override public int getVariant() { return item.getVariant(stack); }
        @Override public void setVariant(int variant) { item.setVariant(stack, variant); }
    }
}
