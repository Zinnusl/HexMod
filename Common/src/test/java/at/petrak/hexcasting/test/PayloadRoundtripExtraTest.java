package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.msgs.MsgCastParticleS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgNewWallScrollS2C;
import at.petrak.hexcasting.common.msgs.MsgOpenSpellGuiS2C;
import at.petrak.hexcasting.common.msgs.MsgRecalcWallScrollDisplayS2C;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Companion to {@link PayloadRoundtripTest} covering the remaining IMessage / CustomPacketPayload
 * subtypes. Same shape: serialize through each payload's {@code serialize}, read back via its
 * {@code deserialize}, assert field equality plus zero leftover bytes.
 * <p>
 * Some payloads carry registry-backed types that don't round-trip cleanly under
 * {@link RegistryAccess#EMPTY} — we pick representative instances that avoid those edges
 * (e.g. empty ItemStacks, empty ravenmind tags) so the tests validate the primitive codec paths
 * without pulling in the full data-component registry plumbing.
 */
public final class PayloadRoundtripExtraTest {
    @BeforeAll
    public static void bootstrap() {
        // Vanilla bootstrap (via TestBootstrap) is needed so ByteBufCodecs.registry can look up
        // the ENTITY_TYPE registry when (de)serializing ClientboundAddEntityPacket.
        TestBootstrap.init();
    }

    private static RegistryFriendlyByteBuf buf() {
        // MsgCastParticleS2C hard-codes RegistryAccess.EMPTY in its serialize; the matching buf
        // here must also be EMPTY so the provider view the deserializer sees is consistent.
        // For the entity-packet case BuiltInRegistries is the fallback anyway.
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    public void castParticleRoundtrip() {
        // Covers the 8×double + int + NBT(compound) path. Using an empty-stack FrozenPigment
        // avoids needing the full DataComponent registry to encode ItemStack.save(provider) — only
        // the UUID + absent-stack branch of FrozenPigment runs.
        var spray = new ParticleSpray(
            new Vec3(1.0, 2.0, 3.0),
            new Vec3(-0.5, 0.25, 0.75),
            0.5,
            1.25,
            12
        );
        var pigment = new FrozenPigment(
            ItemStack.EMPTY,
            UUID.fromString("11112222-3333-4444-5555-666677778888")
        );
        var original = new MsgCastParticleS2C(spray, pigment);

        var buf = buf();
        original.serialize(buf);
        var back = MsgCastParticleS2C.deserialize(buf);

        assertEquals(spray.getPos(), back.spray().getPos(), "spray pos");
        assertEquals(spray.getVel(), back.spray().getVel(), "spray vel");
        assertEquals(spray.getFuzziness(), back.spray().getFuzziness(), 0.0, "fuzziness");
        assertEquals(spray.getSpread(), back.spray().getSpread(), 0.0, "spread");
        assertEquals(spray.getCount(), back.spray().getCount(), "count");
        // Empty-stack FrozenPigment round-trips to DEFAULT (per FrozenPigment.fromNBT empty-stack
        // branch) but its UUID survives — assert the UUID explicitly instead of full equality.
        assertEquals(pigment.owner(), back.colorizer().owner(), "pigment owner UUID");
        assertEquals(0, buf.readableBytes(), "no leftover bytes");
    }

    @Test
    public void newSpellPatternS2CRoundtrip() {
        // boolean + enum + int + list<CompoundTag> + optional<CompoundTag>. Index picked non-zero
        // to catch a sign-flipping serialize bug.
        var desc1 = new CompoundTag();
        desc1.putString("text", "first-item");
        var desc2 = new CompoundTag();
        desc2.putInt("n", 42);
        var ravenmind = new CompoundTag();
        ravenmind.putString("text", "raven");

        var info = new ExecutionClientView(
            false,
            ResolvedPatternType.EVALUATED,
            List.of(desc1, desc2),
            ravenmind
        );
        var original = new MsgNewSpellPatternS2C(info, 7);

        var buf = buf();
        original.serialize(buf);
        var back = MsgNewSpellPatternS2C.deserialize(buf);

        assertEquals(original.index(), back.index(), "index");
        assertEquals(info.isStackClear(), back.info().isStackClear(), "isStackClear");
        assertEquals(info.getResolutionType(), back.info().getResolutionType(), "resolution type");
        assertEquals(info.getStackDescs().size(), back.info().getStackDescs().size(), "stack size");
        assertEquals(info.getStackDescs(), back.info().getStackDescs(), "stack tags");
        assertEquals(ravenmind, back.info().getRavenmind(), "ravenmind");
        assertEquals(0, buf.readableBytes(), "no leftover bytes");
    }

    @Test
    public void newSpellPatternS2CNoRavenmindRoundtrip() {
        // The optional<CompoundTag> path matters most when ravenmind is absent — that's the common
        // case for "stack with no focus". Covers buf.writeOptional(Optional.empty()) → single
        // boolean false.
        var info = new ExecutionClientView(
            true,
            ResolvedPatternType.UNRESOLVED,
            List.of(),
            null
        );
        var original = new MsgNewSpellPatternS2C(info, 0);

        var buf = buf();
        original.serialize(buf);
        var back = MsgNewSpellPatternS2C.deserialize(buf);

        assertEquals(0, back.index());
        assertTrue(back.info().isStackClear(), "isStackClear");
        assertEquals(ResolvedPatternType.UNRESOLVED, back.info().getResolutionType());
        assertEquals(0, back.info().getStackDescs().size(), "empty stack descs");
        assertNull(back.info().getRavenmind(), "ravenmind absent");
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void newSpiralPatternsRoundtrip() {
        // UUID + List<HexPattern-via-NBT> + int. Picks two patterns with distinct start dirs to
        // catch an off-by-one in HexPattern.fromNBT/serializeToNBT.
        var uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        var p1 = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var p2 = HexPattern.fromAngles("wewew", HexDir.SOUTH_WEST);
        var original = new MsgNewSpiralPatternsS2C(uuid, List.of(p1, p2), 42);

        var buf = buf();
        original.serialize(buf);
        var back = MsgNewSpiralPatternsS2C.deserialize(buf);

        assertEquals(uuid, back.playerUUID(), "UUID");
        assertEquals(42, back.lifetime(), "lifetime");
        assertEquals(2, back.patterns().size(), "pattern count");
        assertEquals(p1.anglesSignature(), back.patterns().get(0).anglesSignature());
        assertEquals(p1.getStartDir(), back.patterns().get(0).getStartDir());
        assertEquals(p2.anglesSignature(), back.patterns().get(1).anglesSignature());
        assertEquals(p2.getStartDir(), back.patterns().get(1).getStartDir());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void newSpiralPatternsEmptyListRoundtrip() {
        // Empty-list covers the zero-length-prefix path without touching HexPattern NBT.
        var uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var original = new MsgNewSpiralPatternsS2C(uuid, List.of(), 0);
        var buf = buf();
        original.serialize(buf);
        var back = MsgNewSpiralPatternsS2C.deserialize(buf);

        assertEquals(uuid, back.playerUUID());
        assertEquals(0, back.lifetime());
        assertEquals(0, back.patterns().size());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void openSpellGuiRoundtrip() {
        // enum + list<ResolvedPattern via NBT> + list<CompoundTag> + CompoundTag + varInt.
        // This is the biggest payload — a staff-open sends the whole UI state at once.
        var pattern = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var rp = new ResolvedPattern(pattern, new HexCoord(2, -1), ResolvedPatternType.EVALUATED);

        var stackTag = new CompoundTag();
        stackTag.putDouble("val", 3.14);

        var ravenmind = new CompoundTag();
        ravenmind.putString("text", "the raven");

        var original = new MsgOpenSpellGuiS2C(
            InteractionHand.OFF_HAND,
            List.of(rp),
            List.of(stackTag),
            ravenmind,
            5
        );

        var buf = buf();
        original.serialize(buf);
        var back = MsgOpenSpellGuiS2C.deserialize(buf);

        assertEquals(InteractionHand.OFF_HAND, back.hand(), "hand");
        assertEquals(1, back.patterns().size(), "patterns count");
        assertEquals(pattern.anglesSignature(), back.patterns().get(0).getPattern().anglesSignature());
        assertEquals(pattern.getStartDir(), back.patterns().get(0).getPattern().getStartDir());
        assertEquals(new HexCoord(2, -1), back.patterns().get(0).getOrigin(), "origin");
        assertEquals(ResolvedPatternType.EVALUATED, back.patterns().get(0).getType());
        assertEquals(List.of(stackTag), back.stack(), "stack descs");
        assertEquals(ravenmind, back.ravenmind(), "ravenmind");
        assertEquals(5, back.parenCount(), "paren count");
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void openSpellGuiEmptyRoundtrip() {
        // Main-hand, empty stack, empty patterns, empty ravenmind, zero parens — exercises every
        // "collection is empty" branch in one payload.
        var original = new MsgOpenSpellGuiS2C(
            InteractionHand.MAIN_HAND,
            List.of(),
            List.of(),
            new CompoundTag(),
            0
        );

        var buf = buf();
        original.serialize(buf);
        var back = MsgOpenSpellGuiS2C.deserialize(buf);

        assertEquals(InteractionHand.MAIN_HAND, back.hand());
        assertEquals(0, back.patterns().size());
        assertEquals(0, back.stack().size());
        assertEquals(new CompoundTag(), back.ravenmind());
        assertEquals(0, back.parenCount());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void newWallScrollRoundtrip() {
        // Inner packet (STREAM_CODEC, registry-backed EntityType) + BlockPos + byte + ItemStack
        // (non-empty because ItemStack.STREAM_CODEC rejects EMPTY) + boolean + varInt.
        // Both the entity-type + item-id + data-component registries need to be available —
        // TestBootstrap.init() runs Bootstrap.bootStrap() which populates BuiltInRegistries, and
        // the buf below wraps that registry-of-registries so lookup() finds the entries.
        var inner = new ClientboundAddEntityPacket(
            1234,
            UUID.fromString("deadbeef-dead-beef-dead-beefdeadbeef"),
            10.5, 64.0, -3.25,
            0.0f, 90.0f,
            EntityType.AREA_EFFECT_CLOUD,
            0,
            new Vec3(0.0, 0.0, 0.0),
            0.0
        );
        var scrollStack = new ItemStack(Items.PAPER);
        var original = new MsgNewWallScrollS2C(
            inner,
            new BlockPos(5, 6, 7),
            Direction.NORTH,
            scrollStack,
            true,
            3
        );

        // ClientboundAddEntityPacket's STREAM_CODEC encodes EntityType via the ENTITY_TYPE registry,
        // and ItemStack.STREAM_CODEC encodes Item + DataComponent via their respective registries.
        // Wrap BuiltInRegistries.REGISTRY so both lookups succeed.
        var buf = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY)
        );
        original.serialize(buf);
        var back = MsgNewWallScrollS2C.deserialize(buf);

        assertEquals(1234, back.inner().getId(), "entity id");
        assertEquals(inner.getUUID(), back.inner().getUUID(), "entity UUID");
        assertEquals(EntityType.AREA_EFFECT_CLOUD, back.inner().getType(), "entity type");
        assertEquals(new BlockPos(5, 6, 7), back.pos(), "pos");
        assertEquals(Direction.NORTH, back.dir(), "dir");
        assertEquals(Items.PAPER, back.scrollItem().getItem(), "scroll item type");
        assertEquals(scrollStack.getCount(), back.scrollItem().getCount(), "scroll item count");
        assertTrue(back.showsStrokeOrder(), "stroke order");
        assertEquals(3, back.blockSize(), "block size");
        assertEquals(0, buf.readableBytes(), "no leftover bytes");
    }

    @Test
    public void recalcWallScrollDisplayRoundtrip() {
        // Simplest of the six: varInt + boolean. A regression here would desync stroke-order
        // toggles between players in a shared world.
        var original = new MsgRecalcWallScrollDisplayS2C(987654, true);
        var buf = buf();
        original.serialize(buf);
        var back = MsgRecalcWallScrollDisplayS2C.deserialize(buf);
        assertEquals(original, back, "record equality");
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void recalcWallScrollDisplayFalseRoundtrip() {
        // Second case flips the boolean — guards against a "hard-coded true" serialize bug.
        var original = new MsgRecalcWallScrollDisplayS2C(0, false);
        var buf = buf();
        original.serialize(buf);
        var back = MsgRecalcWallScrollDisplayS2C.deserialize(buf);
        assertEquals(0, back.entityId());
        assertFalse(back.showStrokeOrder());
        assertEquals(0, buf.readableBytes());
    }
}
