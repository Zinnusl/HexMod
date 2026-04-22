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
 * TODO(port-1.21): client-side initializer skeleton. Wires particles, shaders, block
 * entity renderers, model layers, and the spellcasting GUI scroll listener using the
 * NeoForge 1.21 client events.
 * <p>
 * Deferred:
 * <ul>
 *   <li>GLOBAL_ITEM_COLORS / GLOBAL_BLOCK_COLORS — the old self-mixin path is dead;
 *       on 1.21 hook {@code RegisterColorHandlersEvent.Item/Block} and register directly.</li>
 *   <li>{@code EntityRenderersEvent.AddLayers} — the skin/layer API shifted and hex's
 *       AltioraLayer needs a bespoke pass.</li>
 *   <li>Model-bake / model-register events — needed for the dynamic pattern textures.</li>
 *   <li>{@code ClientPlayerNetworkEvent.LoggingIn} callback still connects, but the
 *       {@code PatternRegistryManifest.processRegistry(null)} call needs a ServerLevel
 *       now; leave it running server-side for now.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HexAPI.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ForgeHexClientInitializer {
    public static ItemColors GLOBAL_ITEM_COLORS;
    public static BlockColors GLOBAL_BLOCK_COLORS;

    public static void register(IEventBus modBus) {
        modBus.addListener(ForgeHexClientInitializer::clientInit);

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
        });

        evBus.addListener((InputEvent.MouseScrollingEvent e) -> {
            var cancel = ShiftScrollListener.onScrollInGameplay(e.getScrollDeltaY());
            e.setCanceled(cancel);
        });

        HexInterop.clientInit();
    }

    public static void clientInit(FMLClientSetupEvent evt) {
        evt.enqueueWork(() -> {
            RegisterClientStuff.init();
            // TODO(port-1.21): restore color-provider registration via
            // RegisterColorHandlersEvent.Item + .Block on the mod bus.
        });
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
