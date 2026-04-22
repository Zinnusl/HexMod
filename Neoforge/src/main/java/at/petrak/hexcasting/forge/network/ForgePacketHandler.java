package at.petrak.hexcasting.forge.network;

import net.neoforged.neoforge.network.PacketDistributor;

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
 * The old imperative {@code NETWORK.registerMessage} chain here has no direct
 * equivalent — registration happens per-payload on the mod bus. This class is kept
 * as a compile stub so callers of {@code ForgePacketHandler.init()} still resolve;
 * real wiring lives in a future {@code ForgeHexInitializer} subscription.
 */
public class ForgePacketHandler {
    public static void init() {
        // no-op until payloads are ported. See TODO above.
    }

    /**
     * Legacy callers (e.g. {@code ForgeHexInitializer}'s StartTracking listener) use
     * this to send to a specific player. Return the vanilla {@link PacketDistributor}
     * pattern — individual messages will reach it once they implement
     * {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload}.
     */
    public static PacketDistributor getNetwork() {
        return PacketDistributor.PLAYER;
    }
}
