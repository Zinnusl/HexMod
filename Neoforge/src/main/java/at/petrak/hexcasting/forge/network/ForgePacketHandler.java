package at.petrak.hexcasting.forge.network;

import at.petrak.hexcasting.common.msgs.IMessage;
import at.petrak.hexcasting.common.msgs.MsgBeepS2C;
import at.petrak.hexcasting.common.msgs.MsgCastParticleS2C;
import at.petrak.hexcasting.common.msgs.MsgClearSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgNewWallScrollS2C;
import at.petrak.hexcasting.common.msgs.MsgOpenSpellGuiS2C;
import at.petrak.hexcasting.common.msgs.MsgRecalcWallScrollDisplayS2C;
import at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S;
import at.petrak.hexcasting.forge.network.MsgAltioraUpdateAck;
import at.petrak.hexcasting.forge.network.MsgBrainsweepAck;
import at.petrak.hexcasting.forge.network.MsgPigmentUpdateAck;
import at.petrak.hexcasting.forge.network.MsgSentinelStatusUpdateAck;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ForgePacketHandler {
    public static final String PROTOCOL_VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(ForgePacketHandler::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar(PROTOCOL_VERSION);

        reg.playToServer(MsgNewSpellPatternC2S.TYPE, MsgNewSpellPatternC2S.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> {
                MinecraftServer server = ctx.player().getServer();
                if (server != null && ctx.player() instanceof ServerPlayer sp) {
                    msg.handle(server, sp);
                }
            }));
        reg.playToServer(MsgShiftScrollC2S.TYPE, MsgShiftScrollC2S.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> {
                MinecraftServer server = ctx.player().getServer();
                if (server != null && ctx.player() instanceof ServerPlayer sp) {
                    msg.handle(server, sp);
                }
            }));

        reg.playToClient(MsgBeepS2C.TYPE, MsgBeepS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgBeepS2C.handle(msg)));
        reg.playToClient(MsgClearSpiralPatternsS2C.TYPE, MsgClearSpiralPatternsS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgClearSpiralPatternsS2C.handle(msg)));
        reg.playToClient(MsgNewSpellPatternS2C.TYPE, MsgNewSpellPatternS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgNewSpellPatternS2C.handle(msg)));
        reg.playToClient(MsgNewSpiralPatternsS2C.TYPE, MsgNewSpiralPatternsS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgNewSpiralPatternsS2C.handle(msg)));
        reg.playToClient(MsgRecalcWallScrollDisplayS2C.TYPE, MsgRecalcWallScrollDisplayS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgRecalcWallScrollDisplayS2C.handle(msg)));
        reg.playToClient(MsgNewWallScrollS2C.TYPE, MsgNewWallScrollS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgNewWallScrollS2C.handle(msg)));
        reg.playToClient(MsgCastParticleS2C.TYPE, MsgCastParticleS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgCastParticleS2C.handle(msg)));
        reg.playToClient(MsgOpenSpellGuiS2C.TYPE, MsgOpenSpellGuiS2C.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgOpenSpellGuiS2C.handle(msg)));

        reg.playToClient(MsgBrainsweepAck.TYPE, MsgBrainsweepAck.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgBrainsweepAck.handle(msg)));
        reg.playToClient(MsgPigmentUpdateAck.TYPE, MsgPigmentUpdateAck.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgPigmentUpdateAck.handle(msg)));
        reg.playToClient(MsgSentinelStatusUpdateAck.TYPE, MsgSentinelStatusUpdateAck.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgSentinelStatusUpdateAck.handle(msg)));
        reg.playToClient(MsgAltioraUpdateAck.TYPE, MsgAltioraUpdateAck.CODEC,
            (msg, ctx) -> ctx.enqueueWork(() -> MsgAltioraUpdateAck.handle(msg)));
    }

    /**
     * Legacy no-op hook retained for callers that haven't migrated yet.
     */
    public static void init() {
    }

    public static Facade getNetwork() {
        return FACADE;
    }

    private static final Facade FACADE = new Facade();

    public static final class Facade {
        public void sendTo(ServerPlayer player, IMessage message) {
            PacketDistributor.sendToPlayer(player, message);
        }

        public void sendToServer(IMessage message) {
            PacketDistributor.sendToServer(message);
        }

        public void sendToAll(MinecraftServer server, IMessage message) {
            PacketDistributor.sendToAllPlayers(message);
        }

        public void sendToAllAround(MinecraftServer server, net.minecraft.world.level.Level level,
                                    net.minecraft.world.phys.Vec3 pos, double radius, IMessage message) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (sp.level() != level) continue;
                if (sp.distanceToSqr(pos.x, pos.y, pos.z) <= radius * radius) {
                    PacketDistributor.sendToPlayer(sp, message);
                }
            }
        }

        public void sendToTrackingEntity(net.minecraft.world.entity.Entity entity, IMessage message) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, message);
        }
    }
}
