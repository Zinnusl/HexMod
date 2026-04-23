package at.petrak.hexcasting.test;

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
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Minimal {@link IXplatAbstractions} used only under {@code Common/src/test}. Provides the two
 * things {@code HexActions.<clinit>} actually touches — an empty {@link #getActionRegistry()} and
 * {@link #isModPresent(String)} returning false — plus empty registries for the handful of other
 * lookups Kotlin-side code may reach from action constructors. Everything else throws
 * {@link UnsupportedOperationException} so accidental server/client-only paths fail loudly in tests
 * instead of returning misleading nulls.
 * <p>
 * Registered via {@code META-INF/services/at.petrak.hexcasting.xplat.IXplatAbstractions}
 * in the test resources so {@link java.util.ServiceLoader} picks it up.
 */
public final class StubXplatAbstractions implements IXplatAbstractions {
    private static <T> Registry<T> freshRegistry(net.minecraft.resources.ResourceKey<Registry<T>> key) {
        // 1.21 MappedRegistry(ResourceKey, Lifecycle) — no freeze(): tests may want to
        // populate these registries in @BeforeAll if they exercise downstream lookups.
        return new MappedRegistry<>(key, Lifecycle.stable());
    }

    private final Registry<ActionRegistryEntry> actionRegistry = freshRegistry(HexRegistries.ACTION);
    private final Registry<SpecialHandler.Factory<?>> specialHandlerRegistry = freshRegistry(HexRegistries.SPECIAL_HANDLER);
    private final Registry<IotaType<?>> iotaTypeRegistry = freshRegistry(HexRegistries.IOTA_TYPE);
    private final Registry<Arithmetic> arithmeticRegistry = freshRegistry(HexRegistries.ARITHMETIC);
    private final Registry<ContinuationFrame.Type<?>> continuationTypeRegistry = freshRegistry(HexRegistries.CONTINUATION_TYPE);
    private final Registry<EvalSound> evalSoundRegistry = freshRegistry(HexRegistries.EVAL_SOUND);

    @Override public Platform platform() { return Platform.FORGE; }
    @Override public boolean isModPresent(String id) { return false; }
    @Override public boolean isPhysicalClient() { return false; }
    @Override public void initPlatformSpecific() {}

    @Override public Registry<ActionRegistryEntry> getActionRegistry() { return actionRegistry; }
    @Override public Registry<SpecialHandler.Factory<?>> getSpecialHandlerRegistry() { return specialHandlerRegistry; }
    @Override public Registry<IotaType<?>> getIotaTypeRegistry() { return iotaTypeRegistry; }
    @Override public Registry<Arithmetic> getArithmeticRegistry() { return arithmeticRegistry; }
    @Override public Registry<ContinuationFrame.Type<?>> getContinuationTypeRegistry() { return continuationTypeRegistry; }
    @Override public Registry<EvalSound> getEvalSoundRegistry() { return evalSoundRegistry; }

    @Override public IXplatTags tags() {
        return new IXplatTags() {
            @Override public TagKey<Item> amethystDust() { return null; }
            @Override public TagKey<Item> gems() { return null; }
        };
    }

    // ---- Everything below is not needed at HexActions.<clinit>. Fail loudly. ----

    private static <T> T unsupported() {
        throw new UnsupportedOperationException("StubXplatAbstractions does not support this — test needs a real platform binding");
    }

    @Override public void sendPacketToPlayer(ServerPlayer target, IMessage packet) { unsupported(); }
    @Override public void sendPacketNear(Vec3 pos, double radius, ServerLevel dimension, IMessage packet) { unsupported(); }
    @Override public void sendPacketTracking(Entity entity, IMessage packet) { unsupported(); }
    @Override public Packet<ClientGamePacketListener> toVanillaClientboundPacket(IMessage message) { return unsupported(); }
    @Override public void setBrainsweepAddlData(Mob mob) { unsupported(); }
    @Override public boolean isBrainswept(Mob mob) { return false; }
    @Override public @Nullable FrozenPigment setPigment(Player target, @Nullable FrozenPigment colorizer) { return unsupported(); }
    @Override public void setSentinel(Player target, @Nullable Sentinel sentinel) { unsupported(); }
    @Override public void setFlight(ServerPlayer target, @Nullable FlightAbility flight) { unsupported(); }
    @Override public void setAltiora(Player target, @Nullable AltioraAbility altiora) { unsupported(); }
    @Override public void setStaffcastImage(ServerPlayer target, @Nullable CastingImage image) { unsupported(); }
    @Override public void setPatterns(ServerPlayer target, List<ResolvedPattern> patterns) { unsupported(); }
    @Override public @Nullable FlightAbility getFlight(ServerPlayer player) { return unsupported(); }
    @Override public @Nullable AltioraAbility getAltiora(Player player) { return unsupported(); }
    @Override public FrozenPigment getPigment(Player player) { return unsupported(); }
    @Override public @Nullable Sentinel getSentinel(Player player) { return unsupported(); }
    @Override public CastingVM getStaffcastVM(ServerPlayer player, InteractionHand hand) { return unsupported(); }
    @Override public List<ResolvedPattern> getPatternsSavedInUi(ServerPlayer player) { return unsupported(); }
    @Override public void clearCastingData(ServerPlayer player) { unsupported(); }
    @Override public @Nullable ADMediaHolder findMediaHolder(ItemStack stack) { return null; }
    @Override public @Nullable ADMediaHolder findMediaHolder(ServerPlayer player) { return null; }
    @Override public @Nullable ADIotaHolder findDataHolder(ItemStack stack) { return null; }
    @Override public @Nullable ADIotaHolder findDataHolder(Entity entity) { return null; }
    @Override public @Nullable ADHexHolder findHexHolder(ItemStack stack) { return null; }
    @Override public @Nullable ADVariantItem findVariantHolder(ItemStack stack) { return null; }
    @Override public boolean isPigment(ItemStack stack) { return false; }
    @Override public ColorProvider getColorProvider(FrozenPigment pigment) { return unsupported(); }
    @Override public Item.Properties addEquipSlotFabric(EquipmentSlot slot) { return new Item.Properties(); }
    @Override public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> func, Block... blocks) {
        // NeoForge ATs make BlockEntityType.BlockEntitySupplier public in the main source set,
        // but tests don't get the AT applied so we can't reference it by name. Use reflection to
        // invoke Builder.of(BlockEntitySupplier, Block[]) via a java.lang.reflect.Proxy.
        try {
            Class<?> supplierClass = Class.forName("net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier");
            Object supplier = java.lang.reflect.Proxy.newProxyInstance(
                supplierClass.getClassLoader(),
                new Class<?>[]{supplierClass},
                (proxy, method, args) -> {
                    // The SAM has a single method `create(BlockPos, BlockState)`.
                    return func.apply((BlockPos) args[0], (BlockState) args[1]);
                });
            java.lang.reflect.Method builderOf = net.minecraft.world.level.block.entity.BlockEntityType.Builder.class
                .getDeclaredMethod("of", supplierClass, Block[].class);
            Object builder = builderOf.invoke(null, supplier, blocks);
            java.lang.reflect.Method build = builder.getClass().getMethod("build", com.mojang.datafixers.types.Type.class);
            @SuppressWarnings("unchecked")
            BlockEntityType<T> result = (BlockEntityType<T>) build.invoke(builder, (Object) null);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Couldn't construct BlockEntityType via reflection", e);
        }
    }
    @Override public boolean tryPlaceFluid(Level level, InteractionHand hand, BlockPos pos, Fluid fluid) { return false; }
    @Override public boolean drainAllFluid(Level level, BlockPos pos) { return false; }
    @Override public boolean isCorrectTierForDrops(Tier tier, BlockState bs) { return true; }
    @Override public Ingredient getUnsealedIngredient(ItemStack stack) { return Ingredient.of(stack); }
    @Override public LootItemCondition.Builder isShearsCondition() { return unsupported(); }
    @Override public String getModName(String namespace) { return namespace; }
    @Override public boolean isBreakingAllowed(ServerLevel world, BlockPos pos, BlockState state, @Nullable Player player) { return true; }
    @Override public boolean isPlacingAllowed(ServerLevel world, BlockPos pos, ItemStack blockStack, @Nullable Player player) { return true; }
    @Override public PehkuiInterop.ApiAbstraction getPehkuiApi() { return unsupported(); }
}
