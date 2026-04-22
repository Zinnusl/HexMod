package at.petrak.hexcasting.common.msgs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21: Vanilla networking moved to {@link CustomPacketPayload} backed by a
 * {@code StreamCodec<RegistryFriendlyByteBuf, T>}. Hex's legacy IMessage contract
 * (a serialize(FriendlyByteBuf) + getFabricId pair) is stubbed here so the existing
 * message types still compile; platform plumbing that actually registers packets needs
 * to wire real CustomPacketPayload.Type + streamCodec for each message.
 * <p>
 * TODO(port-1.21): implement CustomPacketPayload on each Msg* and remove this shim.
 */
public interface IMessage extends CustomPacketPayload {
    default net.minecraft.network.FriendlyByteBuf toBuf() {
        var ret = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        serialize(ret);
        return ret;
    }

    default void serialize(net.minecraft.network.FriendlyByteBuf buf) {
    }

    default ResourceLocation getFabricId() {
        return ResourceLocation.fromNamespaceAndPath("hexcasting", "stub");
    }

    @Override
    default CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return new CustomPacketPayload.Type<>(getFabricId());
    }
}
