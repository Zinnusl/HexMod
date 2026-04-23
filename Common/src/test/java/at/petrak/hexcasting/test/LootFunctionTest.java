package at.petrak.hexcasting.test;

import at.petrak.hexcasting.common.lib.HexLootFunctions;
import at.petrak.hexcasting.common.loot.AmethystReducerFunc;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loot functions in 1.21 use a codec-based registration pattern ({@code LootItemFunctionType}
 * is now a record carrying the MapCodec). Hex's three loot functions — the amethyst reducer,
 * pattern-scroll populator, and ancient-cypher hex populator — all have their own codec shape
 * baked into data-pack JSON files. A silent codec shape change would mean all hex-generated
 * loot drops silently stop mutating their stacks.
 * <p>
 * We round-trip the codec and check that:
 * <ul>
 *   <li>the three {@link LootItemFunctionType}s are registered under the right hex ids,</li>
 *   <li>the amethyst-reducer's {@code delta} field survives a JSON round-trip,</li>
 *   <li>the reducer's behavior is actually applied to matching stacks (it's pure, doesn't need
 *       a live LootContext for the static helper).</li>
 * </ul>
 */
public final class LootFunctionTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void lootFunctionTypesAreRegistered() {
        Map<ResourceLocation, Object> funcs = new LinkedHashMap<>();
        HexLootFunctions.registerSerializers((type, loc) -> funcs.put(loc, type));

        assertFalse(funcs.isEmpty(), "HexLootFunctions.registerSerializers registered nothing");

        assertTrue(funcs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "amethyst_shard_reducer")),
            "amethyst_shard_reducer — used in every amethyst loot table");
        assertTrue(funcs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "pattern_scroll")),
            "pattern_scroll — used in dungeon loot chests");
        assertTrue(funcs.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "hex_cypher")),
            "hex_cypher — used in ancient-cypher loot drops");

        for (var e : funcs.entrySet()) {
            assertNotNull(e.getValue(),
                () -> e.getKey() + " -> null LootItemFunctionType");
            assertEquals("hexcasting", e.getKey().getNamespace());
        }
    }

    @Test
    public void amethystReducerCodecRoundtrip() {
        // Codec shape: {"function": "hexcasting:amethyst_shard_reducer", "delta": -0.5}
        // Missing or renamed "delta" would silently stop reducing amethyst drops.
        var original = new AmethystReducerFunc(List.of(), -0.5);

        var encoded = AmethystReducerFunc.CODEC.codec().encodeStart(JsonOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(),
            () -> "encode: " + encoded.error().map(Object::toString).orElse("?"));
        var json = encoded.result().get();
        assertTrue(json.isJsonObject(), "codec emits a JsonObject");

        JsonObject obj = json.getAsJsonObject();
        assertTrue(obj.has("delta"), "'delta' is the data-pack field name — don't rename");
        assertEquals(-0.5, obj.get("delta").getAsDouble(), 0.0);

        var decoded = AmethystReducerFunc.CODEC.codec().parse(JsonOps.INSTANCE, json);
        assertTrue(decoded.result().isPresent(),
            () -> "decode: " + decoded.error().map(Object::toString).orElse("?"));
        assertEquals(-0.5, decoded.result().get().delta, 0.0);
    }

    @Test
    public void amethystReducerHalvesAmethystCount() {
        // Regression target: the reducer is what makes vanilla amethyst drops smaller for players
        // without the unlocker advancement. The logic is pure so we can test without a LootContext.
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 4);
        var result = AmethystReducerFunc.doStatic(stack, null, -0.5);
        assertEquals(2, result.getCount(), "4 shards * (1 + -0.5) = 2");
    }

    @Test
    public void amethystReducerIgnoresNonAmethystStacks() {
        // The function only triggers on AMETHYST_SHARD; passing a different item must leave it
        // untouched. Guards against overly-broad stack.is() checks after a port.
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 4);
        var result = AmethystReducerFunc.doStatic(stack, null, -0.5);
        assertEquals(4, result.getCount(), "non-amethyst stack count unchanged");
    }

    @Test
    public void amethystReducerCodecRejectsMissingDelta() {
        // Without the "delta" field the codec should fail to decode rather than default to 0.
        // A silent default-zero would disable the reducer entirely while still appearing to load.
        JsonObject obj = new JsonObject();
        // No delta.
        var decoded = AmethystReducerFunc.CODEC.codec().parse(JsonOps.INSTANCE, obj);
        assertTrue(decoded.error().isPresent() || decoded.result().isEmpty(),
            "missing delta field must fail decoding, not silently default");
    }
}
