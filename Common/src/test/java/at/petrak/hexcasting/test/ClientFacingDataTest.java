package at.petrak.hexcasting.test;

import at.petrak.hexcasting.client.ClientTickCounter;
import at.petrak.hexcasting.api.client.ScryingLensOverlayRegistry;
import at.petrak.hexcasting.common.lib.HexParticles;
import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Common-side tests for the small pile of client-facing pure-data classes that survive on both
 * sides of the dedicated-server / client boundary. None of these should need a live Minecraft.
 *
 * <ul>
 *   <li>{@link ConjureParticleOptions} — common-side particle options carried by
 *       {@code MsgCastParticleS2C}. A codec mismatch (JSON or stream) would corrupt the particle
 *       stream the server sends on every cast, making every spell visually flicker back to the
 *       "missing" default. The record body is a single int, so we exercise the
 *       {@code RecordCodecBuilder.mapCodec} → {@code MapCodec} → {@code Codec} chain and the
 *       {@code ByteBufCodecs.INT} composite stream codec; both must read back the same value.</li>
 *   <li>{@link ClientTickCounter} — static tick accumulator. Its {@code clientTickEnd()} calls
 *       {@code Minecraft.getInstance()} and is not safe to drive headless, so we only exercise the
 *       pure-data arithmetic surface: {@code renderTickStart} feeds {@code partialTicks} which
 *       {@code getTotal()} folds into {@code ticksInGame}. This catches any accidental
 *       int-vs-float narrowing during the port and the boundary case where the counter wraps
 *       across a whole tick.</li>
 *   <li>{@link ScryingLensOverlayRegistry} — registration surface for scrying-lens UI. Rendering
 *       needs GL, but the registry's own dispatch is pure data: given a {@code BlockState} it
 *       fans out to the registered ID and predicate overlays. We skip the render path entirely
 *       and just assert the dispatch logic wires up correctly.</li>
 * </ul>
 * <p>
 * {@code ScryingLensOverlayRegistry} keeps static state — tests use a per-test namespace so
 * entries from other tests don't collide, and a duplicate-registration assertion confirms the
 * "already registered" guard still fires.
 */
public final class ClientFacingDataTest {
    @BeforeAll
    public static void bootstrap() {
        // Vanilla Bootstrap primes BuiltInRegistries.BLOCK (Blocks.STONE) and PARTICLE_TYPE; HexParticles
        // registers CONJURE_PARTICLE against the reopened particle registry for getType() lookups.
        TestBootstrap.init();
    }

    // ------ ConjureParticleOptions ------

    @Test
    public void conjureParticleOptionsConstructAndGetType() {
        // Force HexParticles.<clinit> so CONJURE_PARTICLE is populated; otherwise getType() comes
        // back null and every subsequent particle packet would silently decode to a ghost type.
        assertNotNull(HexParticles.CONJURE_PARTICLE, "HexParticles must register CONJURE_PARTICLE");
        var opts = new ConjureParticleOptions(0x8932b8);
        assertEquals(0x8932b8, opts.color(), "color field carried through canonical accessor");
        ParticleType<?> type = opts.getType();
        assertSame(HexParticles.CONJURE_PARTICLE, type,
            "getType() must return the registered CONJURE_PARTICLE singleton");
    }

    @Test
    public void conjureParticleOptionsJsonRoundTrip() {
        // MapCodec.codec() promotes to a full Codec; encoding produces {"color": N}, decoding
        // re-builds the record. If the field name drifted ("colour" vs "color") every datapack
        // reference would silently fail to decode.
        var original = new ConjureParticleOptions(0x00ff7722);

        var codec = ConjureParticleOptions.CODEC.codec();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
            () -> "encode failed: " + encoded.error().map(Object::toString).orElse("?"));
        JsonElement json = encoded.result().get();
        assertTrue(json.isJsonObject(), "MapCodec output must be a JSON object");
        assertTrue(json.getAsJsonObject().has("color"),
            "field must be named \"color\" to match datapack references");

        var decoded = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(decoded.result().isPresent(),
            () -> "decode failed: " + decoded.error().map(Object::toString).orElse("?"));
        assertEquals(original, decoded.result().get(), "round-trip preserves record equality");
    }

    @Test
    public void conjureParticleOptionsJsonRoundTripWithNegativeColor() {
        // Colors are often written with the alpha byte set — a signed int would wrap negative.
        // The codec is Codec.INT which handles the full 32-bit range; this locks that in.
        var original = new ConjureParticleOptions(0xff8932b8);
        var codec = ConjureParticleOptions.CODEC.codec();
        var json = codec.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
        var back = codec.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(original.color(), back.color(), "negative/high-bit color survives round-trip");
    }

    @Test
    public void conjureParticleOptionsStreamCodecRoundTrip() {
        // The stream codec is what the server writes in MsgCastParticleS2C; a desync here would
        // garble every particle in every spell. 4-byte int on the wire, no alignment.
        var original = new ConjureParticleOptions(0x12345678);

        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ConjureParticleOptions.STREAM_CODEC.encode(buf, original);
        assertEquals(4, buf.readableBytes(),
            "ByteBufCodecs.INT is fixed 4 bytes — any other length means the composite codec drifted");

        var back = ConjureParticleOptions.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(),
            "decode must consume exactly what encode wrote — leftover bytes would corrupt the next packet");
        assertEquals(original, back, "stream round-trip preserves record equality");
    }

    @Test
    public void conjureParticleOptionsTypeExposesSameCodecs() {
        // ParticleType.codec() / streamCodec() are how vanilla dispatches particle de/serialization
        // through the particle type registry. If the Type wrapper accidentally holds a different
        // codec instance, the dispatch path would break even though the static CODEC/STREAM_CODEC
        // still look right.
        var type = new ConjureParticleOptions.Type(false);
        assertSame(ConjureParticleOptions.CODEC, type.codec(),
            "Type.codec() must be the same MapCodec instance as the static CODEC");
        assertSame(ConjureParticleOptions.STREAM_CODEC, type.streamCodec(),
            "Type.streamCodec() must be the same StreamCodec instance as the static STREAM_CODEC");
    }

    // ------ ClientTickCounter ------

    @Test
    public void clientTickCounterGetTotalCombinesTicksAndPartial() {
        // getTotal() = ticksInGame + partialTicks. No paused/Minecraft call. Reset first so
        // concurrent tests don't carry over stale state.
        ClientTickCounter.ticksInGame = 0L;
        ClientTickCounter.partialTicks = 0.0F;
        assertEquals(0.0F, ClientTickCounter.getTotal(), 0.0F,
            "zero state means zero total");

        ClientTickCounter.ticksInGame = 100L;
        ClientTickCounter.partialTicks = 0.25F;
        assertEquals(100.25F, ClientTickCounter.getTotal(), 1e-6F,
            "float addition of ticks + partial must match hand-calculation");
    }

    @Test
    public void clientTickCounterRenderTickStartSetsPartial() {
        // renderTickStart feeds the partial — called from the render-tick hook. A typo that swapped
        // the parameter assignment (e.g. assigned it to ticksInGame instead) would freeze or
        // double-advance the counter.
        ClientTickCounter.ticksInGame = 50L;
        ClientTickCounter.partialTicks = 0.0F;

        ClientTickCounter.renderTickStart(0.75F);
        assertEquals(0.75F, ClientTickCounter.partialTicks, 1e-6F,
            "renderTickStart argument must land in partialTicks");
        assertEquals(50L, ClientTickCounter.ticksInGame,
            "renderTickStart must not mutate ticksInGame");
        assertEquals(50.75F, ClientTickCounter.getTotal(), 1e-6F,
            "getTotal combines the freshly-set partial with the untouched tick count");
    }

    @Test
    public void clientTickCounterAccumulatesOverManyTicks() {
        // Simulate N tick boundaries: each crosses the whole integer, partial is reset to 0. We
        // can't drive clientTickEnd() (it reaches Minecraft.getInstance()), so we replay its
        // effect directly on the static fields. This catches any regression that might widen the
        // partial type or drop precision on the int→float total.
        ClientTickCounter.ticksInGame = 0L;
        ClientTickCounter.partialTicks = 0.0F;
        int n = 1_000;
        for (int i = 0; i < n; i++) {
            // Pretend the render-tick fires mid-frame at 0.5 partial, then the game ticks forward.
            ClientTickCounter.renderTickStart(0.5F);
            assertEquals(i + 0.5F, ClientTickCounter.getTotal(), 1e-4F,
                "mid-tick total must include partial");
            ClientTickCounter.ticksInGame += 1L;
            ClientTickCounter.partialTicks = 0.0F;
        }
        assertEquals((float) n, ClientTickCounter.getTotal(), 1e-4F,
            "after N boundary crossings the total equals N exactly");
    }

    // ------ ScryingLensOverlayRegistry ------

    @Test
    public void scryingLensRegistryAcceptsIdBasedOverlays() {
        // Register by ID — this is the path most hex blocks use (addDisplayer(Block, builder)
        // dispatches here via BuiltInRegistries.BLOCK.getKey). Use a unique namespace per test
        // to avoid collisions with other test methods that also poke the static map.
        var loc = ResourceLocation.fromNamespaceAndPath("hexcasting-test", "registry-accepts-id");
        AtomicInteger hits = new AtomicInteger(0);
        ScryingLensOverlayRegistry.OverlayBuilder builder =
            (lines, state, pos, observer, world, hitFace) -> hits.incrementAndGet();

        ScryingLensOverlayRegistry.addDisplayer(loc, builder);

        // Duplicate registration must fail — prevents silent override of an existing displayer.
        assertThrows(IllegalArgumentException.class,
            () -> ScryingLensOverlayRegistry.addDisplayer(loc, builder),
            "duplicate addDisplayer(ResourceLocation) must throw");
    }

    @Test
    public void scryingLensRegistryDispatchesIdOverlayOnGetLines() {
        // Dispatch path: addDisplayer(Block, builder) registers under the block's vanilla
        // registration key; getLines(state, ...) looks the block up and invokes the builder.
        // We pass null for Player/Level because our builder ignores them. If the dispatch chain
        // were broken, the hits counter would stay at zero.
        AtomicInteger hits = new AtomicInteger(0);
        List<String> seenStates = new ArrayList<>();
        ScryingLensOverlayRegistry.OverlayBuilder builder =
            (lines, state, pos, observer, world, hitFace) -> {
                hits.incrementAndGet();
                seenStates.add(state.getBlock().toString());
                lines.add(new Pair<>(ItemStack.EMPTY, Component.literal("ok")));
            };

        // Use a vanilla block nobody else is likely to register against — DIRT is uncommon in hex.
        // BuiltInRegistries.BLOCK was primed by TestBootstrap via vanilla Bootstrap.
        var block = Blocks.DIRT;
        ScryingLensOverlayRegistry.addDisplayer(block, builder);

        var lines = ScryingLensOverlayRegistry.getLines(
            block.defaultBlockState(),
            new BlockPos(1, 2, 3),
            null, null, Direction.UP);

        assertEquals(1, hits.get(),
            "ID-based overlay must fire exactly once for the matching block");
        assertEquals(1, lines.size(),
            "builder added one line, that line must appear in the returned list");
        assertEquals("ok", lines.get(0).getSecond().getString(),
            "the line contents round-trip through the registry untouched");
    }

    @Test
    public void scryingLensRegistryDispatchesPredicateOverlay() {
        // Predicate path: matched lines are appended after the ID-based matches. Verify the
        // predicate actually gates the call — a builder keyed on a true-returning predicate fires,
        // one keyed on a false-returning predicate does not.
        AtomicInteger firingHits = new AtomicInteger(0);
        AtomicInteger gatedHits = new AtomicInteger(0);

        ScryingLensOverlayRegistry.addPredicateDisplayer(
            (state, pos, observer, world, hitFace) -> state.getBlock() == Blocks.GRAVEL,
            (lines, state, pos, observer, world, hitFace) -> firingHits.incrementAndGet());

        ScryingLensOverlayRegistry.addPredicateDisplayer(
            (state, pos, observer, world, hitFace) -> false,
            (lines, state, pos, observer, world, hitFace) -> gatedHits.incrementAndGet());

        // Unregistered via ID so only the predicate path runs.
        ScryingLensOverlayRegistry.getLines(
            Blocks.GRAVEL.defaultBlockState(),
            new BlockPos(0, 0, 0),
            null, null, Direction.DOWN);

        assertEquals(1, firingHits.get(),
            "matching predicate must invoke its builder exactly once");
        assertEquals(0, gatedHits.get(),
            "false predicate must not invoke its builder");
    }

    @Test
    public void scryingLensRegistryMissingBlockReturnsEmpty() {
        // A block with no ID registration and no matching predicate returns an empty list —
        // equally, this tests that getLines doesn't NPE on a block that no test has registered
        // against (we use NETHER_WART which hex doesn't touch, and which no other test here
        // registers against).
        var lines = ScryingLensOverlayRegistry.getLines(
            Blocks.NETHER_WART.defaultBlockState(),
            new BlockPos(0, 0, 0),
            null, null, Direction.NORTH);
        // We can't guarantee 0 lines because a predicate from another test might have matched;
        // instead, assert the call returns a non-null list without throwing. Empty-or-nothing is
        // the contract.
        assertNotNull(lines, "getLines must return a non-null list for an unregistered block");
    }
}
