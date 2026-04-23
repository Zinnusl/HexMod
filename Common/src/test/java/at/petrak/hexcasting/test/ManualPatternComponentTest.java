package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.interop.patchouli.ManualPatternComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vazkii.patchouli.api.IVariable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ManualPatternComponent#onVariablesAvailable}. The method
 * resolves {@code patternsRaw} + {@code strokeOrderRaw} through a lookup closure plus
 * a {@link HolderLookup.Provider}, then parses the resulting JSON list into
 * {@link HexPattern}s stored in {@code resolvedPatterns}. A miss here leaves book
 * pattern pages with an empty pattern list (silent) — same failure shape as the
 * {@code op_id}-missing header bug that just landed.
 * <p>
 * We construct {@link RegistryAccess#EMPTY} as the {@link HolderLookup.Provider} —
 * the method's provider argument is threaded into
 * {@link IVariable#asListOrSingleton(HolderLookup.Provider)} and from there into the
 * Patchouli {@code VariableHelper}. Since Patchouli's deserializer doesn't actually
 * poke the provider unless the wrapped element is a tag / registry reference, the
 * empty provider works fine for our JSON-pattern payloads.
 */
public final class ManualPatternComponentTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    /**
     * A list of raw pattern objects threaded through the lookup must come out as a
     * list of {@link HexPattern}s with identical {@code startDir} + signature. This is
     * the end-to-end happy path: the book provides {@code patterns = [{startdir, signature}, …]},
     * the template binds {@code "patterns": "#patterns"}, and the component unpacks.
     */
    @Test
    public void onVariablesAvailableResolvesListOfPatterns() throws Exception {
        // The two patterns we expect to come back. These mirror real book content —
        // "mask" has a two-pattern variant that the stackmanip entry uses.
        HexPattern pat1 = HexPattern.fromAngles("aeea", HexDir.SOUTH_EAST);
        HexPattern pat2 = HexPattern.fromAngles("eada", HexDir.EAST);

        // Build the raw JSON list the way Patchouli would hand it to us after
        // substituting "#patterns". We wire the substitution in the lookup closure below.
        JsonArray rawList = new JsonArray();
        rawList.add(rawPatternJson(pat1));
        rawList.add(rawPatternJson(pat2));

        var comp = new ManualPatternComponent();
        setField(comp, "patternsRaw", "#patterns");
        setField(comp, "strokeOrderRaw", "false");

        UnaryOperator<IVariable> lookup = iv -> {
            // Patchouli's lookup resolves variables of the form "#name" into their
            // bound value. We honour that contract here — any other input passes through.
            String s = iv.unwrap().isJsonPrimitive() ? iv.asString() : "";
            if ("#patterns".equals(s)) {
                return IVariable.wrap(rawList, RegistryAccess.EMPTY);
            }
            return iv;
        };

        comp.onVariablesAvailable(lookup, RegistryAccess.EMPTY);

        @SuppressWarnings("unchecked")
        List<HexPattern> resolved = (List<HexPattern>) getField(comp, "resolvedPatterns");
        assertNotNull(resolved, "resolvedPatterns must be populated, not left null");
        assertEquals(2, resolved.size(), "two input patterns → two resolved patterns");
        assertEquals(pat1.anglesSignature(), resolved.get(0).anglesSignature());
        assertEquals(pat1.getStartDir(), resolved.get(0).getStartDir());
        assertEquals(pat2.anglesSignature(), resolved.get(1).anglesSignature());
        assertEquals(pat2.getStartDir(), resolved.get(1).getStartDir());

        // getPatterns(lookup) must return the same list — it's the render-time accessor.
        List<HexPattern> viaGetter = comp.getPatterns(lookup);
        assertEquals(resolved, viaGetter,
            "getPatterns returns the list resolvedPatterns built in onVariablesAvailable");
    }

    /**
     * A single pattern object (not wrapped in an array) is accepted as a singleton
     * list — that's what {@code IVariable.asListOrSingleton} does. The
     * {@code manual_pattern_nosig} entries on the {@code numbers} page use this shape
     * when a page has exactly one pattern.
     */
    @Test
    public void onVariablesAvailableAcceptsSingletonPattern() throws Exception {
        HexPattern only = HexPattern.fromAngles("aqaa", HexDir.SOUTH_EAST);
        JsonObject rawSingle = rawPatternJson(only);

        var comp = new ManualPatternComponent();
        setField(comp, "patternsRaw", "#patterns");
        setField(comp, "strokeOrderRaw", "");

        UnaryOperator<IVariable> lookup = iv -> {
            String s = iv.unwrap().isJsonPrimitive() ? iv.asString() : "";
            if ("#patterns".equals(s)) {
                return IVariable.wrap(rawSingle, RegistryAccess.EMPTY);
            }
            return iv;
        };

        comp.onVariablesAvailable(lookup, RegistryAccess.EMPTY);

        @SuppressWarnings("unchecked")
        List<HexPattern> resolved = (List<HexPattern>) getField(comp, "resolvedPatterns");
        assertEquals(1, resolved.size(), "singleton object → one resolved pattern");
        assertEquals(only.anglesSignature(), resolved.get(0).anglesSignature());
        assertEquals(only.getStartDir(), resolved.get(0).getStartDir());
    }

    /**
     * {@code strokeOrderRaw == ""} resolves to false — {@link IVariable#asBoolean}'s
     * implementation short-circuits empty strings to {@code false} regardless of the
     * passed default. That's a Patchouli-historical legacy behaviour we rely on: the
     * book page's {@code stroke_order} field being absent means "don't draw stroke
     * order arrows" (which matches Patchouli's bare-pattern rendering default).
     * <p>
     * If {@code IVariable.asBoolean} is ever tightened to respect the default for empty
     * strings, the production code would start drawing stroke-order arrows on pages
     * that never asked for them — this test is the canary.
     */
    @Test
    public void strokeOrderEmptyRawResolvesFalse() throws Exception {
        var comp = new ManualPatternComponent();
        setField(comp, "patternsRaw", "#patterns");
        setField(comp, "strokeOrderRaw", ""); // the "missing in JSON" shape

        JsonArray empty = new JsonArray();
        UnaryOperator<IVariable> lookup = iv -> {
            String s = iv.unwrap().isJsonPrimitive() ? iv.asString() : "";
            if ("#patterns".equals(s)) return IVariable.wrap(empty, RegistryAccess.EMPTY);
            return iv;
        };

        comp.onVariablesAvailable(lookup, RegistryAccess.EMPTY);
        assertFalse(comp.showStrokeOrder(),
            "empty strokeOrderRaw → IVariable.asBoolean short-circuits to false");
    }

    /**
     * {@code strokeOrderRaw = "true"} enables the stroke-order overlay. This is the
     * positive case for the casting/101 entry where the book explicitly asks for the
     * arrows to be shown.
     */
    @Test
    public void strokeOrderRespectsExplicitTrue() throws Exception {
        var comp = new ManualPatternComponent();
        setField(comp, "patternsRaw", "#patterns");
        setField(comp, "strokeOrderRaw", "true");

        JsonArray empty = new JsonArray();
        UnaryOperator<IVariable> lookup = iv -> {
            String s = iv.unwrap().isJsonPrimitive() ? iv.asString() : "";
            if ("#patterns".equals(s)) return IVariable.wrap(empty, RegistryAccess.EMPTY);
            return iv;
        };

        comp.onVariablesAvailable(lookup, RegistryAccess.EMPTY);
        assertTrue(comp.showStrokeOrder(),
            "strokeOrderRaw=\"true\" must resolve to true");
    }

    /** {@code strokeOrderRaw = "false"} disables the stroke-order overlay. */
    @Test
    public void strokeOrderRespectsExplicitFalse() throws Exception {
        var comp = new ManualPatternComponent();
        setField(comp, "patternsRaw", "#patterns");
        setField(comp, "strokeOrderRaw", "false");

        JsonArray empty = new JsonArray();
        UnaryOperator<IVariable> lookup = iv -> {
            String s = iv.unwrap().isJsonPrimitive() ? iv.asString() : "";
            if ("#patterns".equals(s)) return IVariable.wrap(empty, RegistryAccess.EMPTY);
            return iv;
        };

        comp.onVariablesAvailable(lookup, RegistryAccess.EMPTY);
        assertFalse(comp.showStrokeOrder(),
            "strokeOrderRaw=\"false\" must resolve to false");
    }

    // ---- helpers ---------------------------------------------------------------

    private static JsonObject rawPatternJson(HexPattern pat) {
        JsonObject obj = new JsonObject();
        obj.add("startdir", new JsonPrimitive(pat.getStartDir().name()));
        obj.add("signature", new JsonPrimitive(pat.anglesSignature()));
        return obj;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        // Walk up through the superclass chain; ManualPatternComponent's own fields
        // live on the subclass but `resolvedPatterns` stays here, and we share this
        // helper with AbstractPatternComponent-level reads for future assertions.
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        throw new NoSuchFieldException(name + " on " + cls);
    }
}
