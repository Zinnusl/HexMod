package at.petrak.hexcasting.common.msgs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public interface IMessage extends CustomPacketPayload {
    void serialize(RegistryFriendlyByteBuf buf);

    ResourceLocation getFabricId();

    @Override
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();

    static <T extends IMessage> CustomPacketPayload.Type<T> makeType(ResourceLocation id) {
        return new CustomPacketPayload.Type<>(id);
    }

    static <T extends IMessage> StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(Function<RegistryFriendlyByteBuf, T> deserialize) {
        return StreamCodec.of((buf, msg) -> msg.serialize(buf), deserialize::apply);
    }
}
