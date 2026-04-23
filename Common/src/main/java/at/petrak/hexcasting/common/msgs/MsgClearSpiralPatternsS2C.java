package at.petrak.hexcasting.common.msgs;

import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

public record MsgClearSpiralPatternsS2C(UUID playerUUID) implements IMessage {
    public static final ResourceLocation ID = modLoc("clr_spi_pats_sc");
    public static final CustomPacketPayload.Type<MsgClearSpiralPatternsS2C> TYPE = IMessage.makeType(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MsgClearSpiralPatternsS2C> CODEC = IMessage.streamCodec(MsgClearSpiralPatternsS2C::deserialize);

    @Override
    public CustomPacketPayload.Type<MsgClearSpiralPatternsS2C> type() {
        return TYPE;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static MsgClearSpiralPatternsS2C deserialize(RegistryFriendlyByteBuf buffer) {
        var buf = buffer;
        var player = buf.readUUID();

        return new MsgClearSpiralPatternsS2C(player);
    }

    @Override
    public void serialize(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
    }

    public static void handle(MsgClearSpiralPatternsS2C self) {
        Minecraft.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                var mc = Minecraft.getInstance();
                assert mc.level != null;
                var player = mc.level.getPlayerByUUID(self.playerUUID);
                var stack = IClientXplatAbstractions.INSTANCE.getClientCastingStack(player);
                stack.slowClear();
            }
        });
    }
}
