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
import at.petrak.hexcasting.forge.lib.ForgeHexArgumentTypeRegistry;
import at.petrak.hexcasting.forge.lib.ForgeHexLootMods;
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
 * TODO(port-1.21): rebuild the full event-bus surface — brainswept mob event,
 * LivingConversionEvent, player-position recorder, break-speed veto, tool-strip
 * handling, RegisterPayloadHandlersEvent for each Msg*, EntityAttributeModificationEvent
 * routing, Curios → Accessories registration, creative-tab contents, break-speed
 * predicate, server-started per-world pattern gen, BuildCreativeModeTabContentsEvent,
 * etc. The original was 298 lines of legacy Forge events; each has a distinct 1.21
 * NeoForge equivalent (mostly on {@code modBus.addListener} + the new NeoForge events).
 * This stub handles only the skeleton:
 * <ul>
 *   <li>Hex configs (common/client/server).</li>
 *   <li>DeferredRegister wiring for every hex-owned registry onto the mod bus.</li>
 *   <li>Vanilla-registry additions via RegisterEvent, mirroring the Common binders.</li>
 *   <li>A minimal setup listener that runs hex's cross-platform init.</li>
 * </ul>
 * With this class present the mod loads; actions/items/blocks appear in-world;
 * but anything capability-driven (brainsweep, pigment, altiora, sentinel, flight,
 * addl-data holders) stays inert until those subsystems land.
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
    }
}
