package at.petrak.hexcasting.forge;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.client.ClientTickCounter;
import at.petrak.hexcasting.client.RegisterClientStuff;
import at.petrak.hexcasting.client.ShiftScrollListener;
import at.petrak.hexcasting.client.gui.PatternTooltipComponent;
import at.petrak.hexcasting.client.model.HexModelLayers;
import at.petrak.hexcasting.client.render.HexAdditionalRenderers;
import at.petrak.hexcasting.client.render.shader.HexShaders;
import at.petrak.hexcasting.common.casting.PatternRegistryManifest;
import at.petrak.hexcasting.common.lib.HexParticles;
import at.petrak.hexcasting.common.misc.PatternTooltip;
import at.petrak.hexcasting.interop.HexInterop;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.util.function.Function;

/**
 * Client-side initializer for the NeoForge 1.21 side. Subscribes particles, shaders,
 * block entity renderers, model layers, GUI overlay passes, the spellcasting scroll
 * listener, and the tick counter. Colour-provider registration is a no-op on this
 * platform because hex doesn't currently register any block/item colour handlers
 * cross-platform; if one is added later, wire it through
 * {@code RegisterColorHandlersEvent.Item/Block} on the mod bus. AltioraLayer skin
 * injection and dynamic-pattern model bakery are still deferred.
 */
@EventBusSubscriber(modid = HexAPI.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ForgeHexClientInitializer {
    public static ItemColors GLOBAL_ITEM_COLORS;
    public static BlockColors GLOBAL_BLOCK_COLORS;

    public static void register(IEventBus modBus) {
        modBus.addListener(ForgeHexClientInitializer::clientInit);

        // Explicit mod-bus wiring for the @SubscribeEvent-annotated methods below.
        // @EventBusSubscriber auto-scan is unreliable across dev/production classpaths
        // (the class-file index sometimes skips classes not loaded before scan runs),
        // and when auto-scan misses these, the tooltip factory never registers — then
        // rendering any scroll/slate with a pattern tooltip crashes with
        // "Unknown TooltipComponent: hexcasting.common.misc.PatternTooltip".
        modBus.addListener(ForgeHexClientInitializer::registerTooltipComponents);
        modBus.addListener(ForgeHexClientInitializer::registerRenderers);
        modBus.addListener(ForgeHexClientInitializer::registerEntityLayers);
        modBus.addListener((RegisterShadersEvent evt) -> {
            try {
                registerShaders(evt);
            } catch (IOException e) {
                throw new RuntimeException("hex shader registration failed", e);
            }
        });
        modBus.addListener(ForgeHexClientInitializer::registerParticles);

        var evBus = NeoForge.EVENT_BUS;

        // 1.21: getPartialTick returns DeltaTracker; unwrap to a float partial-tick.
        evBus.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
                HexAdditionalRenderers.overlayLevel(e.getPoseStack(),
                    e.getPartialTick().getGameTimeDeltaPartialTick(true));
            }
        });

        evBus.addListener((RenderGuiEvent.Post e) -> {
            HexAdditionalRenderers.overlayGui(e.getGuiGraphics(),
                e.getPartialTick().getGameTimeDeltaPartialTick(true));
        });

        // 1.21: TickEvent.RenderTickEvent became RenderFrameEvent.Pre/Post; client tick
        // split into ClientTickEvent.Pre/Post.
        evBus.addListener((RenderFrameEvent.Pre e) -> {
            ClientTickCounter.renderTickStart(e.getPartialTick().getGameTimeDeltaPartialTick(true));
        });

        evBus.addListener((ClientTickEvent.Post e) -> {
            ClientTickCounter.clientTickEnd();
            ShiftScrollListener.clientTickEnd();
            // Tick every client-side casting stack so lifetime-based spiral pattern
            // fade-out advances. Fabric's CCClientCastingStack implements
            // ClientTickingComponent so CCA auto-ticks it; NeoForge doesn't, so drive
            // the tick from here instead.
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                for (var player : mc.level.players()) {
                    at.petrak.hexcasting.xplat.IClientXplatAbstractions.INSTANCE
                        .getClientCastingStack(player).tick();
                }
            }
        });

        evBus.addListener((InputEvent.MouseScrollingEvent e) -> {
            var cancel = ShiftScrollListener.onScrollInGameplay(e.getScrollDeltaY());
            e.setCanceled(cancel);
        });

        HexInterop.clientInit();
    }

    public static void clientInit(FMLClientSetupEvent evt) {
        evt.enqueueWork(RegisterClientStuff::init);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent evt) throws IOException {
        HexShaders.init(evt.getResourceProvider(), p -> evt.registerShader(p.getFirst(), p.getSecond()));
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent evt) {
        HexParticles.FactoryHandler.registerFactories(new HexParticles.FactoryHandler.Consumer() {
            @Override
            public <T extends ParticleOptions> void register(ParticleType<T> type, Function<SpriteSet,
                ParticleProvider<T>> constructor) {
                evt.registerSpriteSet(type, constructor::apply);
            }
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        RegisterClientStuff.registerBlockEntityRenderers(evt::registerBlockEntityRenderer);
    }

    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent evt) {
        evt.register(PatternTooltip.class, PatternTooltipComponent::new);
    }

    @SubscribeEvent
    public static void registerEntityLayers(EntityRenderersEvent.RegisterLayerDefinitions evt) {
        HexModelLayers.init(evt::registerLayerDefinition);
    }
}
