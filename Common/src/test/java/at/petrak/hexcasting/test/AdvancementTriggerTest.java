package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.advancements.FailToCastGreatSpellTrigger;
import at.petrak.hexcasting.api.advancements.HexAdvancementTriggers;
import at.petrak.hexcasting.api.advancements.MinMaxLongs;
import at.petrak.hexcasting.api.advancements.OvercastTrigger;
import at.petrak.hexcasting.api.advancements.SpendMediaTrigger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.critereon.MinMaxBounds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.21 changed advancement triggers from the old JSON-deserializer class to a codec-based
 * {@link net.minecraft.advancements.CriterionTrigger} API. Hex's three triggers
 * ({@link OvercastTrigger}, {@link SpendMediaTrigger}, {@link FailToCastGreatSpellTrigger}) all
 * had to be rewritten. The risk is silent regression: if the codec shape changes, all advancement
 * JSON in the mod's data pack still parses into something — but the field names don't line up,
 * so matchers always return ANY and advancements trigger indiscriminately (or never).
 * <p>
 * These tests encode the expected JSON shape and round-trip it through the codec; any drift from
 * the hex data-pack format would show up here.
 */
public final class AdvancementTriggerTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void overcastTriggerInstancesExist() {
        // Trigger instances are static singletons — missing one at init time is how the port
        // crashed with feeble_mind missing. Don't let another go un-noticed.
        assertNotNull(HexAdvancementTriggers.OVERCAST_TRIGGER);
        assertNotNull(HexAdvancementTriggers.SPEND_MEDIA_TRIGGER);
        assertNotNull(HexAdvancementTriggers.FAIL_GREAT_SPELL_TRIGGER);

        assertNotNull(HexAdvancementTriggers.OVERCAST_TRIGGER.codec(), "OVERCAST_TRIGGER.codec() is non-null");
        assertNotNull(HexAdvancementTriggers.SPEND_MEDIA_TRIGGER.codec(), "SPEND_MEDIA_TRIGGER.codec() is non-null");
        assertNotNull(HexAdvancementTriggers.FAIL_GREAT_SPELL_TRIGGER.codec(), "FAIL_GREAT_SPELL_TRIGGER.codec() is non-null");
    }

    @Test
    public void overcastTriggerJsonRoundtrip() {
        // Data pack JSON shape: {"media_generated": {"min": 100}}.
        var codec = HexAdvancementTriggers.OVERCAST_TRIGGER.codec();

        var json = new JsonObject();
        var mediaGen = new JsonObject();
        mediaGen.addProperty("min", 100);
        json.add("media_generated", mediaGen);

        var parsed = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(parsed.result().isPresent(),
            () -> "parse: " + parsed.error().map(Object::toString).orElse("ok"));
        OvercastTrigger.Instance inst = parsed.result().get();

        // Smoke-check the parsed matcher.
        assertFalse(inst.test(50, 0.0, 10f), "50 media_generated is below min=100, no match");
        assertTrue(inst.test(200, 0.0, 10f), "200 media_generated matches min=100");

        // And round-trip the other way.
        var encoded = codec.encodeStart(JsonOps.INSTANCE, inst);
        assertTrue(encoded.result().isPresent());
    }

    @Test
    public void spendMediaTriggerJsonRoundtrip() {
        var codec = HexAdvancementTriggers.SPEND_MEDIA_TRIGGER.codec();

        var json = new JsonObject();
        var spent = new JsonObject();
        spent.addProperty("min", 1000);
        spent.addProperty("max", 5000);
        json.add("media_spent", spent);

        var parsed = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(parsed.result().isPresent());
        SpendMediaTrigger.Instance inst = parsed.result().get();

        assertFalse(inst.test(500, 0), "below min");
        assertTrue(inst.test(2500, 0), "within range");
        assertFalse(inst.test(6000, 0), "above max");
    }

    @Test
    public void failGreatSpellTriggerHasEmptyShape() {
        // No fields except the optional "player" — the contract for this trigger is just
        // "player failed a great spell". Regression would add / rename fields, which data packs
        // couldn't read.
        var codec = HexAdvancementTriggers.FAIL_GREAT_SPELL_TRIGGER.codec();
        var empty = new JsonObject();
        var parsed = codec.parse(JsonOps.INSTANCE, empty);
        assertTrue(parsed.result().isPresent(), "empty JSON parses");
        FailToCastGreatSpellTrigger.Instance inst = parsed.result().get();
        assertTrue(inst.player().isEmpty(), "player field is optional and absent");
    }

    @Test
    public void minMaxLongsBounds() {
        // Long-typed range — vanilla only ships Int/Double/Float. Port risk: number format on
        // the boundary — very large values (media costs go into billions) must not overflow.
        long big = 10_000_000_000L;
        var range = MinMaxLongs.between(1L, big);
        assertTrue(range.matches(big), "boundary value matches");
        assertTrue(range.matches(1L), "lower boundary matches");
        assertFalse(range.matches(big + 1), "above max fails");
        assertFalse(range.matches(0L), "below min fails");

        var codec = MinMaxLongs.CODEC;
        var encoded = codec.encodeStart(JsonOps.INSTANCE, range);
        assertTrue(encoded.result().isPresent());
        JsonElement json = encoded.result().get();

        var decoded = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(decoded.result().isPresent());
        assertTrue(decoded.result().get().matches(big), "decoded still matches");
    }

    @Test
    public void minMaxLongsAnyMatchesEverything() {
        // Default: when a field is omitted from data-pack JSON, the matcher should accept any
        // value. This is the default shape Minecraft's serializer drops onto absent fields.
        var any = MinMaxLongs.ANY;
        assertTrue(any.matches(0L));
        assertTrue(any.matches(Long.MAX_VALUE));
        assertTrue(any.matches(Long.MIN_VALUE));
        assertTrue(any.isAny());
    }

    @Test
    public void overcastTriggerMediaGeneratedFieldNameIsStable() {
        // If someone renames the field, every hex advancement JSON breaks. The literal field
        // name is load-bearing and part of the data-pack API contract.
        OvercastTrigger.Instance inst = new OvercastTrigger.Instance(
            Optional.empty(),
            MinMaxBounds.Ints.atLeast(100),
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY
        );

        var codec = OvercastTrigger.Instance.CODEC;
        var encoded = codec.encodeStart(JsonOps.INSTANCE, inst).result().orElseThrow();
        assertTrue(encoded.isJsonObject(), "encoded form is an object");
        JsonObject obj = encoded.getAsJsonObject();
        assertTrue(obj.has("media_generated"),
            "'media_generated' is the data-pack field name — don't rename");
    }
}
