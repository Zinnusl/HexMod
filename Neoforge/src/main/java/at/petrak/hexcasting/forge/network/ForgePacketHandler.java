package at.petrak.hexcasting.forge.network;

import at.petrak.hexcasting.common.msgs.IMessage;
import net.minecraft.server.level.ServerPlayer;

/**
 * TODO(port-1.21): networking on NeoForge 1.21 moved from SimpleChannel /
 * {@code NetworkRegistry.newSimpleChannel} to {@code RegisterPayloadHandlersEvent}
 * + {@code IPayloadRegistrar} + {@code CustomPacketPayload}-based messages. Each
 * {@code Msg*} record needs:
 * <ul>
 *   <li>a {@code CustomPacketPayload.Type<MsgX>} constant</li>
 *   <li>a {@code StreamCodec<RegistryFriendlyByteBuf, MsgX>} constant</li>
 *   <li>a handler {@code (MsgX, IPayloadContext)} bound in
 *       {@link net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent}</li>
 * </ul>
 * This class is kept as a compile stub so callers of {@code ForgePacketHandler.init()}
 * / {@code getNetwork().sendTo*} still resolve; real wiring lives in a future
 * {@code ForgeHexInitializer} subscription.
 */
public class ForgePacketHandler {
    public static void init() {
        // no-op until payloads are ported. See TODO above.
    }

    /**
     * Stub network facade. The pre-1.20.5 shape exposed {@code PacketDistributor} enum
     * targets ({@code PLAYER}, {@code TRACKING_ENTITY}, …) on this return value; those
     * are gone on 1.21 and replaced by static {@code PacketDistributor.sendToPlayer}
     * et al. that take a {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload}.
     * Return a facade whose send helpers are no-ops for the duration of the port.
     */
    public static Facade getNetwork() {
        return FACADE;
    }

    private static final Facade FACADE = new Facade();

    public static final class Facade {
        public void sendTo(ServerPlayer player, IMessage message) { }
        public void sendToServer(IMessage message) { }
    }
}
