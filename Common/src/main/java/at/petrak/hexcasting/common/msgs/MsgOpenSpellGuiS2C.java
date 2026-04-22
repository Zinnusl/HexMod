package at.petrak.hexcasting.common.msgs;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.List;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

/**
 * Sent server->client when the player opens the spell gui to request the server provide the current stack.
 */
public record MsgOpenSpellGuiS2C(InteractionHand hand, List<ResolvedPattern> patterns,
                                 List<CompoundTag> stack,
                                 CompoundTag ravenmind,
                                 int parenCount
)
    implements IMessage {
    public static final ResourceLocation ID = modLoc("cgui");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static MsgOpenSpellGuiS2C deserialize(RegistryFriendlyByteBuf buffer) {
        var buf = buffer;
        var hand = buf.readEnum(InteractionHand.class);

        // 1.21: readList takes StreamDecoder<? super ByteBuf, T>; inline the typed lambda.
        var patterns = buf.<ResolvedPattern>readList(fbb -> ResolvedPattern.fromNBT((net.minecraft.nbt.CompoundTag) fbb.readNbt(net.minecraft.nbt.NbtAccounter.unlimitedHeap())));

        var stack = buf.<CompoundTag>readList(fbb -> fbb.readNbt());
        var raven = (net.minecraft.nbt.CompoundTag) buf.readNbt(net.minecraft.nbt.NbtAccounter.unlimitedHeap());

        var parenCount = buf.readVarInt();

        return new MsgOpenSpellGuiS2C(hand, patterns, stack, raven, parenCount);
    }

    public void serialize(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(this.hand);

        buf.writeCollection(this.patterns, (fbb, pat) -> fbb.writeNbt(pat.serializeToNBT()));

        buf.writeCollection(this.stack, (fbb, t) -> fbb.writeNbt(t));
        buf.writeNbt(this.ravenmind);

        buf.writeVarInt(this.parenCount);
    }

    public static void handle(MsgOpenSpellGuiS2C msg) {
        Minecraft.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                var mc = Minecraft.getInstance();
                mc.setScreen(
                    new GuiSpellcasting(msg.hand(), msg.patterns(), msg.stack, msg.ravenmind,
                        msg.parenCount));
            }
        });
    }
}
