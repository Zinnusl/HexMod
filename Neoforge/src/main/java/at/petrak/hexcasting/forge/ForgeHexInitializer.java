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
        });

        // Forge-side DeferredRegisters (argument types, loot modifier serializers).
        ForgeHexArgumentTypeRegistry.ARGUMENT_TYPES.register(modBus);
        ForgeHexLootMods.REGISTRY.register(modBus);

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
            HexInterop.init();
        }));

        // RegisterBrewingRecipesEvent fires on the main (NeoForge) event bus at world
        // load. The builder it carries is the PotionBrewing.Builder vanilla uses to
        // seed its recipe list.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent evt) ->
                HexPotions.registerMixes(evt.getBuilder(), evt.getRegistryAccess()));
    }
}
