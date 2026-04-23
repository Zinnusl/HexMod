package at.petrak.hexcasting.forge;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.common.blocks.behavior.HexComposting;
import at.petrak.hexcasting.common.blocks.behavior.HexStrippables;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.HexBlockEntities;
import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexCreativeTabs;
import at.petrak.hexcasting.common.entities.HexEntities;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.lib.HexMobEffects;
import at.petrak.hexcasting.common.lib.HexParticles;
import at.petrak.hexcasting.common.lib.HexPotions;
import at.petrak.hexcasting.common.lib.HexSounds;
import at.petrak.hexcasting.common.misc.AkashicTreeGrower;
import at.petrak.hexcasting.common.recipe.HexRecipeStuffRegistry;
import at.petrak.hexcasting.forge.cap.HexAttachments;
import at.petrak.hexcasting.forge.lib.ForgeHexArgumentTypeRegistry;
import at.petrak.hexcasting.forge.lib.ForgeHexLootMods;
import at.petrak.hexcasting.forge.network.ForgePacketHandler;
import at.petrak.hexcasting.forge.xplat.ForgeXplatImpl;
import at.petrak.hexcasting.interop.HexInterop;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Main {@code @Mod} entry point for the NeoForge 1.21 build. Hex boots by:
 * <ul>
 *   <li>Building the three {@link ModConfigSpec} configs (common/client/server) and
 *       stashing them on the common {@link HexConfig} singleton.</li>
 *   <li>Binding every hex-owned registry and player/mob attachment through its
 *       {@code DeferredRegister} on the mod event bus.</li>
 *   <li>Adding vanilla-registry entries (sounds, items, blocks, block entities, recipe
 *       stuff, particles, attributes, mob effects, potions, creative tabs, entity types)
 *       via a single {@link RegisterEvent} listener that mirrors the cross-platform
 *       binders.</li>
 *   <li>Wiring the CustomPacketPayload handler and the brewing-recipe event.</li>
 *   <li>Running hex's cross-platform setup (composting, strippables, akashic tree,
 *       interop init) on {@link FMLCommonSetupEvent}.</li>
 * </ul>
 * AltioraLayer skin injection, creative-tab entry population via
 * {@code BuildCreativeModeTabContentsEvent}, and the break-speed / tool-strip event
 * listeners are still deferred.
 */
@Mod(HexAPI.MOD_ID)
public class ForgeHexInitializer {
    public ForgeHexInitializer(IEventBus modBus, ModContainer container) {
        initConfig(container);
        initRegistries(modBus);
        initListeners(modBus);
    }

    private static void initConfig(ModContainer container) {
        var common = new ModConfigSpec.Builder().configure(ForgeHexConfig::new);
        var client = new ModConfigSpec.Builder().configure(ForgeHexConfig.Client::new);
        var server = new ModConfigSpec.Builder().configure(ForgeHexConfig.Server::new);
        HexConfig.setCommon(common.getLeft());
        HexConfig.setClient(client.getLeft());
        HexConfig.setServer(server.getLeft());
        container.registerConfig(ModConfig.Type.COMMON, common.getRight());
        container.registerConfig(ModConfig.Type.CLIENT, client.getRight());
        container.registerConfig(ModConfig.Type.SERVER, server.getRight());
    }

    private static void initRegistries(IEventBus modBus) {
        // Unfreeze the root registry so hex can create child registries during setup.
        if (BuiltInRegistries.REGISTRY instanceof MappedRegistry<?> rootRegistry) {
            rootRegistry.unfreeze();
        }

        // Hex-owned DeferredRegisters (populated on the mod bus as NewRegistryEvent fires).
        ForgeXplatImpl.ACTIONS.register(modBus);
        ForgeXplatImpl.SPECIAL_HANDLERS.register(modBus);
        ForgeXplatImpl.IOTA_TYPES.register(modBus);
        ForgeXplatImpl.ARITHMETICS.register(modBus);
        ForgeXplatImpl.CONTINUATION_TYPES.register(modBus);
        ForgeXplatImpl.EVAL_SOUNDS.register(modBus);

        // Vanilla-registry additions via the classic RegisterEvent shape, mirroring the
        // Common-side binders.
        modBus.addListener((RegisterEvent evt) -> {
            bindIfMatching(evt, Registries.SOUND_EVENT, HexSounds::registerSounds);
            bindIfMatching(evt, Registries.CREATIVE_MODE_TAB, HexCreativeTabs::registerCreativeTabs);
            bindIfMatching(evt, Registries.BLOCK, HexBlocks::registerBlocks);
            bindIfMatching(evt, Registries.ITEM, HexBlocks::registerBlockItems);
            bindIfMatching(evt, Registries.BLOCK_ENTITY_TYPE, HexBlockEntities::registerTiles);
            bindIfMatching(evt, Registries.ITEM, HexItems::registerItems);
            bindIfMatching(evt, Registries.RECIPE_SERIALIZER, HexRecipeStuffRegistry::registerSerializers);
            bindIfMatching(evt, Registries.RECIPE_TYPE, HexRecipeStuffRegistry::registerTypes);
            bindIfMatching(evt, Registries.ENTITY_TYPE, HexEntities::registerEntities);
            bindIfMatching(evt, Registries.ATTRIBUTE, HexAttributes::register);
            bindIfMatching(evt, Registries.MOB_EFFECT, HexMobEffects::register);
            bindIfMatching(evt, Registries.POTION, HexPotions::register);
            bindIfMatching(evt, Registries.PARTICLE_TYPE, HexParticles::registerParticles);
            bindIfMatching(evt, Registries.LOOT_FUNCTION_TYPE,
                at.petrak.hexcasting.common.lib.HexLootFunctions::registerSerializers);

            // Hex's own custom registries — populated through the DeferredRegisters on
            // ForgeXplatImpl. RegisterEvent fires for these too once the DR attaches to
            // the mod bus (earlier in initRegistries).
            bindIfMatching(evt, at.petrak.hexcasting.common.lib.HexRegistries.ACTION,
                at.petrak.hexcasting.common.lib.hex.HexActions::register);
            bindIfMatching(evt, at.petrak.hexcasting.common.lib.HexRegistries.IOTA_TYPE,
                at.petrak.hexcasting.common.lib.hex.HexIotaTypes::registerTypes);
            bindIfMatching(evt, at.petrak.hexcasting.common.lib.HexRegistries.SPECIAL_HANDLER,
                at.petrak.hexcasting.common.lib.hex.HexSpecialHandlers::register);
            bindIfMatching(evt, at.petrak.hexcasting.common.lib.HexRegistries.ARITHMETIC,
                at.petrak.hexcasting.common.lib.hex.HexArithmetics::register);
            bindIfMatching(evt, at.petrak.hexcasting.common.lib.HexRegistries.EVAL_SOUND,
                at.petrak.hexcasting.common.lib.hex.HexEvalSounds::register);
        });

        // Forge-side DeferredRegisters (argument types, loot modifier serializers,
        // custom ingredient types).
        ForgeHexArgumentTypeRegistry.ARGUMENT_TYPES.register(modBus);
        ForgeHexLootMods.REGISTRY.register(modBus);
        at.petrak.hexcasting.forge.recipe.ForgeHexIngredients.bootstrap(modBus);

        // Player/mob attachments (pigment, sentinel, altiora, flight, brainswept).
        HexAttachments.ATTACHMENTS.register(modBus);

        // CustomPacketPayload registration for every hex Msg*.
        ForgePacketHandler.register(modBus);
    }

    private static <T> void bindIfMatching(
        RegisterEvent evt,
        ResourceKey<? extends Registry<T>> key,
        Consumer<BiConsumer<T, ResourceLocation>> register
    ) {
        if (evt.getRegistryKey().equals(key)) {
            register.accept((thing, id) -> evt.register(key, id, () -> thing));
        }
    }

    private static void initListeners(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent evt) -> evt.enqueueWork(() -> {
            HexComposting.setup();
            HexStrippables.init();
            AkashicTreeGrower.init();
            at.petrak.hexcasting.common.misc.RegisterMisc.register();
            HexInterop.init();
        }));

        // Custom stats + advancement triggers both write into BuiltInRegistries slots
        // (CUSTOM_STAT, TRIGGER_TYPE). On 1.21 those registries freeze before
        // FMLCommonSetupEvent, so run the registrations while RegisterEvent is still
        // dispatching. The registry-key gate is cosmetic — RegisterEvent fires once
        // per registry, and every load runs this exactly when CUSTOM_STAT matches.
        modBus.addListener((net.neoforged.neoforge.registries.RegisterEvent evt) -> {
            if (evt.getRegistryKey().equals(net.minecraft.core.registries.Registries.CUSTOM_STAT)) {
                at.petrak.hexcasting.api.mod.HexStatistics.register();
                at.petrak.hexcasting.api.advancements.HexAdvancementTriggers.registerTriggers();
            }
        });

        // RegisterBrewingRecipesEvent fires on the main (NeoForge) event bus at world
        // load. The builder it carries is the PotionBrewing.Builder vanilla uses to
        // seed its recipe list.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent evt) ->
                HexPotions.registerMixes(evt.getBuilder(), evt.getRegistryAccess()));

        // Brainswept mobs: swallow interactions and preserve the brainswept flag when
        // the entity transforms (zombie <-> villager conversion etc.).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific evt) -> {
                var result = at.petrak.hexcasting.common.misc.BrainsweepingEvents.interactWithBrainswept(
                    evt.getEntity(), evt.getLevel(), evt.getHand(), evt.getTarget(), null);
                if (result == net.minecraft.world.InteractionResult.SUCCESS) {
                    evt.setCancellationResult(result);
                    evt.setCanceled(true);
                }
            });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post evt) ->
                at.petrak.hexcasting.common.misc.BrainsweepingEvents.copyBrainsweepPostTransformation(
                    evt.getEntity(), evt.getOutcome()));

        // Creative tab population: BuildCreativeModeTabContentsEvent fires once per tab
        // on the mod bus. Route both hex tabs through the cross-platform entry lists so
        // HEX and SCROLLS end up with the same items Fabric populates via
        // ItemGroupEvents.MODIFY_ENTRIES_ALL.
        modBus.addListener(
            (net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent evt) -> {
                var tab = evt.getTab();
                HexBlocks.registerBlockCreativeTab(
                    block -> evt.accept(new net.minecraft.world.item.ItemStack(block)), tab);
                HexItems.registerItemCreativeTab(evt, tab);
            });

        // Initial state sync on login: AttachmentType doesn't auto-sync, so fan out the
        // Msg*Ack payloads the cap setters use on mutation so the client has the loaded
        // save data for pigment / sentinel / altiora / flight / brainswept state.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent evt) -> {
                if (!(evt.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
                var xplat = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE;
                xplat.sendPacketToPlayer(sp,
                    new at.petrak.hexcasting.forge.network.MsgPigmentUpdateAck(xplat.getPigment(sp)));
                xplat.sendPacketToPlayer(sp,
                    new at.petrak.hexcasting.forge.network.MsgSentinelStatusUpdateAck(xplat.getSentinel(sp)));
                xplat.sendPacketToPlayer(sp,
                    new at.petrak.hexcasting.forge.network.MsgAltioraUpdateAck(xplat.getAltiora(sp)));
            });

        // Per-tick server bookkeeping: position recorder, flight time-left, altiora grace.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.tick.LevelTickEvent.Post evt) -> {
                if (evt.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                    at.petrak.hexcasting.common.misc.PlayerPositionRecorder.updateAllPlayers(sl);
                    at.petrak.hexcasting.common.casting.actions.spells.OpFlight.tickAllPlayers(sl);
                    at.petrak.hexcasting.common.casting.actions.spells.great.OpAltiora.INSTANCE.checkAllPlayers(sl);
                }
            });

        // Per-world pattern manifest runs once at server start so great patterns get a
        // fresh world-specific hash seed.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartedEvent evt) ->
                at.petrak.hexcasting.common.casting.PatternRegistryManifest.processRegistry(
                    evt.getServer().overworld()));

        // /hexcasting subcommand tree (brainsweep, list-perworld-patterns, recalc, texture-dump).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent evt) ->
                at.petrak.hexcasting.common.lib.HexCommands.register(evt.getDispatcher()));

        // Jeweler hammer gating: prevent breaking unripe amethyst clusters etc. — Fabric
        // uses AttackBlockCallback; on NeoForge the equivalent is PlayerInteractEvent.LeftClickBlock
        // with setCanceled(true) + UseBlock.DENY.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock evt) -> {
                var state = evt.getLevel().getBlockState(evt.getPos());
                if (at.petrak.hexcasting.common.items.ItemJewelerHammer.shouldFailToBreak(
                    evt.getEntity(), state, evt.getPos())) {
                    evt.setCanceled(true);
                    evt.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                }
            });

        // Altiora elytra mimicry: Fabric uses EntityElytraEvents.CUSTOM; NeoForge has no
        // equivalent, so call Player.startFallFlying each tick while altiora is active
        // and the player is airborne. Vanilla's natural ground-contact reset still stops
        // the glide when altiora ends (OpAltiora.checkPlayerCollision fires its own reset
        // on the xplat side).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.tick.EntityTickEvent.Post evt) -> {
                if (evt.getEntity() instanceof net.minecraft.world.entity.player.Player p
                    && !p.level().isClientSide) {
                    var altiora = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.getAltiora(p);
                    if (altiora != null && !p.onGround() && !p.isFallFlying()) {
                        p.startFallFlying();
                    }
                }
            });
    }
}
