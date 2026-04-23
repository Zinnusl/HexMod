package at.petrak.hexcasting.common.blocks.circles.impetuses;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.circles.BlockEntityAbstractImpetus;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.lib.HexBlockEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class BlockEntityRedstoneImpetus extends BlockEntityAbstractImpetus {
    public static final String TAG_STORED_PLAYER = "stored_player";
    public static final String TAG_STORED_PLAYER_PROFILE = "stored_player_profile";

    private GameProfile storedPlayerProfile = null;
    private UUID storedPlayer = null;

    private GameProfile cachedDisplayProfile = null;
    private ItemStack cachedDisplayStack = null;

    public BlockEntityRedstoneImpetus(BlockPos pWorldPosition, BlockState pBlockState) {
        super(HexBlockEntities.IMPETUS_REDSTONE_TILE, pWorldPosition, pBlockState);
    }

    protected @Nullable
    GameProfile getPlayerName() {
        if (this.level instanceof ServerLevel) {
            Player player = getStoredPlayer();
            if (player != null) {
                return player.getGameProfile();
            }
        }

        return this.storedPlayerProfile;
    }

    public void setPlayer(GameProfile profile, UUID player) {
        this.storedPlayerProfile = profile;
        this.storedPlayer = player;
        this.setChanged();
    }

    public void clearPlayer() {
        this.storedPlayerProfile = null;
        this.storedPlayer = null;
    }

    public void updatePlayerProfile() {
        ServerPlayer player = getStoredPlayer();
        if (player != null) {
            GameProfile newProfile = player.getGameProfile();
            if (!newProfile.equals(this.storedPlayerProfile)) {
                this.storedPlayerProfile = newProfile;
                this.setChanged();
            }
        }
    }

    // just feels wrong to use the protected method
    @Nullable
    public ServerPlayer getStoredPlayer() {
        if (this.storedPlayer == null) {
            return null;
        }
        if (!(this.level instanceof ServerLevel slevel)) {
            HexAPI.LOGGER.error("Called getStoredPlayer on the client");
            return null;
        }
        var e = slevel.getEntity(this.storedPlayer);
        if (e instanceof ServerPlayer player) {
            return player;
        } else {
            // if owner is offline then getEntity will return null
            // if e is somehow neither null nor a player, something is very wrong
            if (e != null) {
                HexAPI.LOGGER.error("Entity {} stored in a cleric impetus wasn't a player somehow", e);
            }
            return null;
        }
    }

    public void applyScryingLensOverlay(List<Pair<ItemStack, Component>> lines,
        BlockState state, BlockPos pos, Player observer,
        Level world,
        Direction hitFace) {
        super.applyScryingLensOverlay(lines, state, pos, observer, world, hitFace);

        var name = this.getPlayerName();
        if (name != null) {
            if (!name.equals(cachedDisplayProfile) || cachedDisplayStack == null) {
                cachedDisplayProfile = name;
                var head = new ItemStack(Items.PLAYER_HEAD);
                // 1.21: player-head data is the PROFILE component wrapping ResolvableProfile.
                head.set(net.minecraft.core.component.DataComponents.PROFILE,
                    new net.minecraft.world.item.component.ResolvableProfile(name));
                cachedDisplayStack = head;
            }
            lines.add(new Pair<>(cachedDisplayStack,
                Component.translatable("hexcasting.tooltip.lens.impetus.redstone.bound", name.getName())));
        } else {
            lines.add(new Pair<>(new ItemStack(Items.BARRIER),
                Component.translatable("hexcasting.tooltip.lens.impetus.redstone.bound.none")));
        }
    }

    @Override
    protected void saveModData(CompoundTag tag) {
        super.saveModData(tag);
        if (this.storedPlayer != null) {
            tag.putUUID(TAG_STORED_PLAYER, this.storedPlayer);
        }
        if (this.storedPlayerProfile != null) {
            // 1.21: NbtUtils.writeGameProfile was removed; round-trip through ResolvableProfile codec.
            var rp = new net.minecraft.world.item.component.ResolvableProfile(this.storedPlayerProfile);
            var encoded = net.minecraft.world.item.component.ResolvableProfile.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, rp)
                .result().orElse(null);
            if (encoded instanceof CompoundTag ct) {
                tag.put(TAG_STORED_PLAYER_PROFILE, ct);
            }
        }
    }

    @Override
    protected void loadModData(CompoundTag tag) {
        super.loadModData(tag);
        if (tag.contains(TAG_STORED_PLAYER, Tag.TAG_INT_ARRAY)) {
            this.storedPlayer = tag.getUUID(TAG_STORED_PLAYER);
        } else {
            this.storedPlayer = null;
        }
        if (tag.contains(TAG_STORED_PLAYER_PROFILE, Tag.TAG_COMPOUND)) {
            var rp = net.minecraft.world.item.component.ResolvableProfile.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.getCompound(TAG_STORED_PLAYER_PROFILE))
                .result().orElse(null);
            this.storedPlayerProfile = rp == null ? null : rp.gameProfile();
        } else {
            this.storedPlayerProfile = null;
        }
    }
}
