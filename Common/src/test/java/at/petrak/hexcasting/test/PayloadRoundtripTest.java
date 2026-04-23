package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.msgs.MsgBeepS2C;
import at.petrak.hexcasting.common.msgs.MsgClearSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every {@link at.petrak.hexcasting.common.msgs.IMessage} has a hand-written
 * {@link net.minecraft.network.codec.StreamCodec} pair of {@code serialize}/{@code deserialize}
 * — the two sides must stay in sync. A typo (double written, float read) would corrupt all spell
 * UI traffic silently; in 1.20 the NBT round-trip masked most of these, on 1.21 the typed codec
 * surface is wider.
 * <p>
 * We write each payload through its {@code serialize} method, read back with {@code deserialize},
 * and confirm field equality. Payloads that need {@code RegistryAccess} for registry refs are
 * wrapped in the empty {@link RegistryAccess#EMPTY} — these tests only cover primitive codec
 * paths, not data-component codecs.
 */
public final class PayloadRoundtripTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    private static RegistryFriendlyByteBuf buf() {
        // Empty RegistryAccess is fine — none of the exercised payloads touch registry refs.
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    public void beepRoundtrip() {
        var original = new MsgBeepS2C(new Vec3(1.5, -2.0, 3.25), 7, NoteBlockInstrument.BASS);
        var buf = buf();
        original.serialize(buf);
        var back = MsgBeepS2C.deserialize(buf);
        assertEquals(original, back, "record equality via canonical equals()");
        assertEquals(0, buf.readableBytes(), "serializer wrote exactly what deserializer consumed");
    }

    @Test
    public void shiftScrollRoundtrip() {
        // All five fields — covers double×2 + boolean×3.
        var original = new MsgShiftScrollC2S(1.25, -0.5, true, false, true);
        var buf = buf();
        original.serialize(buf);
        var back = MsgShiftScrollC2S.deserialize(buf);
        assertEquals(original, back);
        assertEquals(0, buf.readableBytes(), "no leftover bytes");
    }

    @Test
    public void clearSpiralPatternsRoundtrip() {
        // Single UUID — 16 bytes. Any off-by-one here would corrupt the player lookup on receive.
        var uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var original = new MsgClearSpiralPatternsS2C(uuid);
        var buf = buf();
        original.serialize(buf);
        var back = MsgClearSpiralPatternsS2C.deserialize(buf);
        assertEquals(original, back);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void newSpellPatternC2SRoundtrip() {
        // This is the packet a player sends when finishing a drawn pattern. Regression here would
        // break all spell casting. Tests the combined: enum + HexPattern NBT + list size + repeated NBT.
        var pattern = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var original = new MsgNewSpellPatternC2S(
            InteractionHand.MAIN_HAND,
            pattern,
            List.of() // Empty list covers the int-length-prefix path without cross-checking ResolvedPattern NBT.
        );
        var buf = buf();
        original.serialize(buf);
        var back = MsgNewSpellPatternC2S.deserialize(buf);
        assertEquals(original.handUsed(), back.handUsed(), "hand");
        assertEquals(pattern.anglesSignature(), back.pattern().anglesSignature(), "pattern angles");
        assertEquals(pattern.getStartDir(), back.pattern().getStartDir(), "pattern startDir");
        assertEquals(0, back.resolvedPatterns().size(), "empty resolved list");
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void truncatedBufferFailsFast() {
        // Serialize a beep but only expose the first 8 bytes — deserialize must throw, not return
        // a half-constructed record. This guards against silent corruption from upstream dropping
        // bytes.
        var original = new MsgBeepS2C(new Vec3(1, 2, 3), 5, NoteBlockInstrument.HARP);
        var fullBuf = buf();
        original.serialize(fullBuf);

        var truncated = new RegistryFriendlyByteBuf(
            Unpooled.buffer().writeBytes(fullBuf, 8),
            RegistryAccess.EMPTY);

        assertThrows(IndexOutOfBoundsException.class,
            () -> MsgBeepS2C.deserialize(truncated),
            "deserialize from truncated buffer must throw");
    }
}
