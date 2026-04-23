package at.petrak.hexcasting.forge.xplat;

import at.petrak.hexcasting.api.addldata.ADHexHolder;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.addldata.ADVariantItem;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.sideeffects.EvalSound;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.player.AltioraAbility;
import at.petrak.hexcasting.api.player.FlightAbility;
import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.common.lib.HexRegistries;
import at.petrak.hexcasting.common.msgs.IMessage;
import at.petrak.hexcasting.interop.pehkui.PehkuiInterop;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import at.petrak.hexcasting.xplat.IXplatTags;
import at.petrak.hexcasting.xplat.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * TODO(port-1.21): large-surface platform bridge. The original 590-line Forge impl is
 * intertwined with the (legacy) Capability system, Curios interop, and the old
 * SimpleChannel packet handler — all three of which need separate 1.21 reimplementations
 * (BlockCapability / ItemCapability / EntityCapability; Accessories; CustomPacketPayload).
 * <p>
 * Until those land, this stub provides:
 * <ul>
 *   <li>the ServiceLoader-discoverable {@link IXplatAbstractions} binding so
 *       {@code IXplatAbstractions.INSTANCE} is non-null at startup,</li>
 *   <li>real answers for the handful of queries that only need vanilla registries or
 *       FML loader state (platform, isModPresent, isPhysicalClient, hex registry lookups),</li>
 *   <li>null / default returns for every capability/pigment/flight/sentinel feature so
 *       callers don't NPE at link time.</li>
 * </ul>
 * Functional behaviour (brainsweep persistence, pigment sync, flight, sentinel, etc.)
 * needs to be re-added on top of 1.21's attached-data / EntityCapability surface once
 * the cap/ subpackage is ported.
 */
public class ForgeXplatImpl implements IXplatAbstractions {

    // Real DeferredRegisters for the hex-owned registries so core hex code can register
    // actions, iota types, etc. The registries themselves are created via makeRegistry.
    public static final DeferredRegister<ActionRegistryEntry> ACTIONS =
        DeferredRegister.create(HexRegistries.ACTION, at.petrak.hexcasting.api.HexAPI.MOD_ID);
    public static final DeferredRegister<SpecialHandler.Factory<?>> SPECIAL_HANDLERS =
        DeferredRegister.create(HexRegistries.SPECIAL_HANDLER, at.petrak.hexcasting.api.HexAPI.MOD_ID);
    public static final DeferredRegister<IotaType<?>> IOTA_TYPES =
        DeferredRegister.create(HexRegistries.IOTA_TYPE, at.petrak.hexcasting.api.HexAPI.MOD_ID);
    public static final DeferredRegister<Arithmetic> ARITHMETICS =
        DeferredRegister.create(HexRegistries.ARITHMETIC, at.petrak.hexcasting.api.HexAPI.MOD_ID);
    public static final DeferredRegister<ContinuationFrame.Type<?>> CONTINUATION_TYPES =
        DeferredRegister.create(HexRegistries.CONTINUATION_TYPE, at.petrak.hexcasting.api.HexAPI.MOD_ID);
    public static final DeferredRegister<EvalSound> EVAL_SOUNDS =
        DeferredRegister.create(HexRegistries.EVAL_SOUND, at.petrak.hexcasting.api.HexAPI.MOD_ID);

    private static final Registry<ActionRegistryEntry> ACTION_REGISTRY = ACTIONS.makeRegistry(b -> b.sync(true));
    private static final Registry<SpecialHandler.Factory<?>> SPECIAL_HANDLER_REGISTRY = SPECIAL_HANDLERS.makeRegistry(b -> b.sync(true));
    private static final Registry<IotaType<?>> IOTA_TYPE_REGISTRY = IOTA_TYPES.makeRegistry(b -> b.sync(true));
    private static final Registry<Arithmetic> ARITHMETIC_REGISTRY = ARITHMETICS.makeRegistry(b -> b.sync(true));
    private static final Registry<ContinuationFrame.Type<?>> CONTINUATION_TYPE_REGISTRY = CONTINUATION_TYPES.makeRegistry(b -> b.sync(true));
    private static final Registry<EvalSound> EVAL_SOUND_REGISTRY = EVAL_SOUNDS.makeRegistry(b -> b.sync(true));

    @Override public Platform platform() { return Platform.FORGE; }
    @Override public boolean isPhysicalClient() { return FMLLoader.getDist() == Dist.CLIENT; }
    @Override public boolean isModPresent(String id) { return ModList.get().isLoaded(id); }
    @Override public void initPlatformSpecific() { }

    @Override public void sendPacketToPlayer(ServerPlayer target, IMessage packet) {
        at.petrak.hexcasting.forge.network.ForgePacketHandler.getNetwork().sendTo(target, packet);
    }
    @Override public void sendPacketNear(Vec3 pos, double radius, ServerLevel dimension, IMessage packet) {
        for (ServerPlayer sp : dimension.players()) {
            if (sp.distanceToSqr(pos.x, pos.y, pos.z) <= radius * radius) {
                at.petrak.hexcasting.forge.network.ForgePacketHandler.getNetwork().sendTo(sp, packet);
            }
        }
    }
    @Override public void sendPacketTracking(Entity entity, IMessage packet) {
        at.petrak.hexcasting.forge.network.ForgePacketHandler.getNetwork().sendToTrackingEntity(entity, packet);
    }
    @Override public Packet<ClientGamePacketListener> toVanillaClientboundPacket(IMessage message) {
        return (Packet<ClientGamePacketListener>) (Packet<?>)
            new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(message);
    }

    // Player/mob attachments — backed by HexAttachments; sync to client via existing Ack messages.
    @Override public void setBrainsweepAddlData(Mob mob) {
        mob.setData(at.petrak.hexcasting.forge.cap.HexAttachments.BRAINSWEPT.get(), Boolean.TRUE);
        if (mob.level() instanceof ServerLevel sl) {
            var ack = at.petrak.hexcasting.forge.network.MsgBrainsweepAck.of(mob);
            for (ServerPlayer sp : sl.players()) {
                sendPacketToPlayer(sp, ack);
            }
        }
    }
    @Override public boolean isBrainswept(Mob mob) {
        return mob.getData(at.petrak.hexcasting.forge.cap.HexAttachments.BRAINSWEPT.get());
    }
    @Override public @Nullable FrozenPigment setPigment(Player target, @Nullable FrozenPigment colorizer) {
        var att = at.petrak.hexcasting.forge.cap.HexAttachments.PIGMENT.get();
        var old = target.getData(att);
        target.setData(att, colorizer != null ? colorizer : FrozenPigment.DEFAULT.get());
        if (target instanceof ServerPlayer sp) {
            sendPacketToPlayer(sp, new at.petrak.hexcasting.forge.network.MsgPigmentUpdateAck(target.getData(att)));
        }
        return old;
    }
    @Override public void setSentinel(Player target, @Nullable Sentinel sentinel) {
        target.setData(at.petrak.hexcasting.forge.cap.HexAttachments.SENTINEL.get(), java.util.Optional.ofNullable(sentinel));
        if (target instanceof ServerPlayer sp) {
            sendPacketToPlayer(sp, new at.petrak.hexcasting.forge.network.MsgSentinelStatusUpdateAck(sentinel));
        }
    }
    @Override public void setFlight(ServerPlayer target, @Nullable FlightAbility flight) {
        target.setData(at.petrak.hexcasting.forge.cap.HexAttachments.FLIGHT.get(), java.util.Optional.ofNullable(flight));
    }
    @Override public void setAltiora(Player target, @Nullable AltioraAbility altiora) {
        target.setData(at.petrak.hexcasting.forge.cap.HexAttachments.ALTIORA.get(), java.util.Optional.ofNullable(altiora));
        if (target instanceof ServerPlayer sp) {
            sendPacketToPlayer(sp, new at.petrak.hexcasting.forge.network.MsgAltioraUpdateAck(altiora));
        }
    }
    @Override public void setStaffcastImage(ServerPlayer target, @Nullable CastingImage image) {
        var tag = image == null ? new net.minecraft.nbt.CompoundTag() : image.serializeToNbt();
        target.setData(at.petrak.hexcasting.forge.cap.HexAttachments.STAFFCAST_IMAGE.get(), tag);
    }
    @Override public void setPatterns(ServerPlayer target, List<ResolvedPattern> patterns) {
        target.setData(at.petrak.hexcasting.forge.cap.HexAttachments.PATTERNS.get(), new java.util.ArrayList<>(patterns));
    }
    @Override public @Nullable FlightAbility getFlight(ServerPlayer player) {
        return player.getData(at.petrak.hexcasting.forge.cap.HexAttachments.FLIGHT.get()).orElse(null);
    }
    @Override public @Nullable AltioraAbility getAltiora(Player player) {
        return player.getData(at.petrak.hexcasting.forge.cap.HexAttachments.ALTIORA.get()).orElse(null);
    }
    @Override public FrozenPigment getPigment(Player player) {
        return player.getData(at.petrak.hexcasting.forge.cap.HexAttachments.PIGMENT.get());
    }
    @Override public @Nullable Sentinel getSentinel(Player player) {
        return player.getData(at.petrak.hexcasting.forge.cap.HexAttachments.SENTINEL.get()).orElse(null);
    }
    @Override public CastingVM getStaffcastVM(ServerPlayer player, InteractionHand hand) {
        var tag = player.getData(at.petrak.hexcasting.forge.cap.HexAttachments.STAFFCAST_IMAGE.get());
        var img = tag.isEmpty()
            ? new CastingImage()
            : CastingImage.loadFromNbt(tag, player.serverLevel());
        return new CastingVM(img, new at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv(player, hand));
    }
    @Override public List<ResolvedPattern> getPatternsSavedInUi(ServerPlayer player) {
        return player.getData(at.petrak.hexcasting.forge.cap.HexAttachments.PATTERNS.get());
    }
    @Override public void clearCastingData(ServerPlayer player) {
        setStaffcastImage(player, null);
        setPatterns(player, List.of());
    }

    // addl-data lookups — instance-dispatched via ADHolderAdapters.
    @Override public @Nullable ADMediaHolder findMediaHolder(ItemStack stack) {
        return at.petrak.hexcasting.forge.cap.adimpl.ADHolderAdapters.media(stack);
    }
    @Override public @Nullable ADMediaHolder findMediaHolder(ServerPlayer player) { return null; }
    @Override public @Nullable ADIotaHolder findDataHolder(ItemStack stack) {
        return at.petrak.hexcasting.forge.cap.adimpl.ADHolderAdapters.iota(stack);
    }
    @Override public @Nullable ADIotaHolder findDataHolder(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
            return new at.petrak.hexcasting.api.addldata.ItemDelegatingEntityIotaHolder.ToItemEntity(itemEntity);
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame itemFrame) {
            return new at.petrak.hexcasting.api.addldata.ItemDelegatingEntityIotaHolder.ToItemFrame(itemFrame);
        }
        if (entity instanceof at.petrak.hexcasting.common.entities.EntityWallScroll wallScroll) {
            return new at.petrak.hexcasting.api.addldata.ItemDelegatingEntityIotaHolder.ToWallScroll(wallScroll);
        }
        return null;
    }
    @Override public @Nullable ADHexHolder findHexHolder(ItemStack stack) {
        return at.petrak.hexcasting.forge.cap.adimpl.ADHolderAdapters.hex(stack);
    }
    @Override public @Nullable ADVariantItem findVariantHolder(ItemStack stack) {
        return at.petrak.hexcasting.forge.cap.adimpl.ADHolderAdapters.variant(stack);
    }

    @Override public boolean isPigment(ItemStack stack) {
        return stack.getItem() instanceof at.petrak.hexcasting.api.item.PigmentItem;
    }
    @Override public ColorProvider getColorProvider(FrozenPigment pigment) {
        var stack = pigment.item();
        if (stack.getItem() instanceof at.petrak.hexcasting.api.item.PigmentItem pi) {
            return pi.provideColor(stack, pigment.owner());
        }
        return ColorProvider.MISSING;
    }

    // Items
    @Override public Item.Properties addEquipSlotFabric(EquipmentSlot slot) { return new Item.Properties(); }

    // Blocks
    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> func,
        Block... blocks) {
        return BlockEntityType.Builder.of(func::apply, blocks).build(null);
    }
    @Override public boolean tryPlaceFluid(Level level, InteractionHand hand, BlockPos pos, Fluid fluid) {
        var state = level.getBlockState(pos);
        if (!state.canBeReplaced(fluid)) return false;
        level.setBlockAndUpdate(pos, fluid.defaultFluidState().createLegacyBlock());
        level.playSound(null, pos, fluid.getFluidType().getSound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY),
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
    @Override public boolean drainAllFluid(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var fluid = state.getFluidState();
        if (fluid.isEmpty()) return false;
        if (state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup) {
            var leftover = pickup.pickupBlock(null, level, pos, state);
            return !leftover.isEmpty();
        }
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        return true;
    }

    // misc
    @Override public boolean isCorrectTierForDrops(Tier tier, BlockState bs) { return true; }

    @Override public Ingredient getUnsealedIngredient(ItemStack stack) {
        // Real impl wraps ForgeUnsealedIngredient via Ingredient.of(ICustomIngredient); needs
        // the IngredientType registered first.
        return Ingredient.of(stack.getItem());
    }

    private static final IXplatTags TAGS_STUB = new IXplatTags() {
        private final net.minecraft.tags.TagKey<Item> AMETHYST_DUST =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dusts/amethyst"));
        private final net.minecraft.tags.TagKey<Item> GEMS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "gems"));
        @Override public net.minecraft.tags.TagKey<Item> amethystDust() { return AMETHYST_DUST; }
        @Override public net.minecraft.tags.TagKey<Item> gems() { return GEMS; }
    };

    @Override public IXplatTags tags() { return TAGS_STUB; }

    @Override public LootItemCondition.Builder isShearsCondition() {
        return net.neoforged.neoforge.common.loot.CanItemPerformAbility.canItemPerformAbility(
            net.neoforged.neoforge.common.ItemAbilities.SHEARS_DIG);
    }

    @Override public String getModName(String namespace) {
        return ModList.get().getModContainerById(namespace)
            .map(ModContainer::getModInfo)
            .map(info -> info.getDisplayName())
            .orElse(namespace);
    }

    @Override public Registry<ActionRegistryEntry> getActionRegistry() { return ACTION_REGISTRY; }
    @Override public Registry<SpecialHandler.Factory<?>> getSpecialHandlerRegistry() { return SPECIAL_HANDLER_REGISTRY; }
    @Override public Registry<IotaType<?>> getIotaTypeRegistry() { return IOTA_TYPE_REGISTRY; }
    @Override public Registry<Arithmetic> getArithmeticRegistry() { return ARITHMETIC_REGISTRY; }
    @Override public Registry<ContinuationFrame.Type<?>> getContinuationTypeRegistry() { return CONTINUATION_TYPE_REGISTRY; }
    @Override public Registry<EvalSound> getEvalSoundRegistry() { return EVAL_SOUND_REGISTRY; }

    @Override public boolean isBreakingAllowed(ServerLevel world, BlockPos pos, BlockState state, @Nullable Player player) {
        var actor = player != null ? player
            : net.neoforged.neoforge.common.util.FakePlayerFactory.get(world, HEXCASTING);
        var event = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(world, pos, state, actor);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }
    @Override public boolean isPlacingAllowed(ServerLevel world, BlockPos pos, ItemStack blockStack, @Nullable Player player) {
        var actor = player != null ? player
            : net.neoforged.neoforge.common.util.FakePlayerFactory.get(world, HEXCASTING);
        var snapshot = java.util.List.of(
            net.neoforged.neoforge.common.util.BlockSnapshot.create(world.dimension(), world, pos));
        var placing = net.minecraft.world.level.block.Block.byItem(blockStack.getItem()).defaultBlockState();
        var event = new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(snapshot.get(0), placing, actor);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    @Override public PehkuiInterop.ApiAbstraction getPehkuiApi() {
        return new PehkuiInterop.ApiAbstraction() {
            @Override public float getScale(Entity entity) { return 1f; }
            @Override public void setScale(Entity entity, float scale) { }
        };
    }
}
