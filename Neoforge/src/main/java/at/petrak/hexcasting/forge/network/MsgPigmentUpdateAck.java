package at.petrak.hexcasting.forge.network;

import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.msgs.IMessage;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

/**
 * Sent server->client to synchronize the status of the sentinel.
 */
public record MsgPigmentUpdateAck(FrozenPigment update) implements IMessage {
    public static final ResourceLocation ID = modLoc("color");
    public static final CustomPacketPayload.Type<MsgPigmentUpdateAck> TYPE = IMessage.makeType(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MsgPigmentUpdateAck> CODEC = IMessage.streamCodec(MsgPigmentUpdateAck::deserialize);

    @Override
    public CustomPacketPayload.Type<MsgPigmentUpdateAck> type() {
        return TYPE;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static MsgPigmentUpdateAck deserialize(RegistryFriendlyByteBuf buffer) {
        var buf = buffer;
        // 1.21: readAnySizeNbt removed; use unlimited NbtAccounter + cast to CompoundTag.
        var tag = (net.minecraft.nbt.CompoundTag) buf.readNbt(net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var colorizer = FrozenPigment.fromNBT(tag, buf.registryAccess());
        return new MsgPigmentUpdateAck(colorizer);
    }

    @Override
    public void serialize(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(this.update.serializeToNBT(buf.registryAccess()));
    }

    public static void handle(MsgPigmentUpdateAck self) {
        Minecraft.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    IXplatAbstractions.INSTANCE.setPigment(player, self.update());
                }
            }
        });
    }
}
