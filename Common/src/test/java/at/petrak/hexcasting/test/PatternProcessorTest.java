package at.petrak.hexcasting.test;

import at.petrak.hexcasting.interop.patchouli.PatternProcessor;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link PatternProcessor}. The processor drives the
 * {@code hexcasting:pattern} / {@code hexcasting:manual_pattern} book templates: it
 * derives the page header from an {@code op_id} and falls back to an explicit
 * {@code header} when present. A slip in the fallback order — or in which translation
 * key it exposes — produces blank / wrong book titles.
 * <p>
 * The real {@code setup(Level, IVariableProvider)} reads {@code level.registryAccess()}
 * unconditionally, so a null Level NPEs. We side-step that by allocating an
 * uninitialized {@link ServerLevel} via {@code sun.misc.Unsafe} and writing a
 * {@link RegistryAccess#EMPTY} into its {@code registryAccess} field — enough for
 * {@code setup} to run without touching the rest of the level graph.
 */
public final class PatternProcessorTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    /**
     * After setup populates {@code translationKey}, {@code process("translation_key")}
     * must return that string wrapped in an {@code IVariable}. Any other key returns null.
     */
    @Test
    public void processWrapsTranslationKeyForMatchingKeyOnly() throws Exception {
        var proc = new PatternProcessor();
        setTranslationKey(proc, "hexcasting.action.get_caster");

        IVariable out = proc.process(null, "translation_key");
        assertNotNull(out, "process(\"translation_key\") must return non-null");
        assertEquals("hexcasting.action.get_caster", out.asString(),
            "process must wrap the translationKey field verbatim");

        IVariable other = proc.process(null, "some_other_key");
        assertNull(other, "process returns null for any key other than translation_key");
    }

    /**
     * setup crashes with a null Level because it unconditionally reads
     * {@code level.registryAccess()}. We document that pre-condition — any future
     * refactor that adds a null-guard here should remove this assertion.
     */
    @Test
    public void setupWithNullLevelNpEs() {
        var proc = new PatternProcessor();
        var vars = new StubVariableProvider().with("op_id", "hexcasting:get_caster");
        assertThrows(NullPointerException.class,
            () -> proc.setup(null, vars),
            "setup unconditionally reads level.registryAccess() — null Level must NPE");
    }

    /**
     * Default-branch contract: with {@code op_id} set and no {@code header}, setup
     * builds {@code "hexcasting.action." + opId}. We exercise the real setup against
     * an Unsafe-allocated Level whose only populated field is {@code registryAccess}.
     * <p>
     * The task's "prefer hexcasting.action.book.&lt;op&gt; when I18n.exists returns
     * true" check is skipped: {@code I18n.exists} requires
     * {@code net.minecraft.locale.Language.getInstance()} to be initialised via the
     * MC client's resource pack pipeline. Without that, every call returns {@code false}
     * (or NPEs on some code paths), so we can't distinguish the override branch from
     * the default one in a headless test.
     */
    @Test
    public void setupDefaultBranchProducesHexcastingActionKey() throws Exception {
        Level fakeLevel = allocateLevelWithRegistryAccess();
        assumeAllocationSucceeded(fakeLevel);

        var proc = new PatternProcessor();
        var vars = new StubVariableProvider().with("op_id", "hexcasting:get_caster");
        proc.setup(fakeLevel, vars);

        IVariable out = proc.process(null, "translation_key");
        assertNotNull(out);
        // I18n.exists returns false in headless tests — so we always take the default path.
        assertEquals("hexcasting.action.hexcasting:get_caster", out.asString(),
            "default (no override) path must be hexcasting.action.<opId>");
    }

    /**
     * Header-branch contract: an explicit {@code header} var is passed straight through
     * with no prefix mangling. This is what {@code manual_pattern_nosig} pages use to
     * override the derived action name with a free-form heading.
     */
    @Test
    public void setupHeaderBranchIsPassthrough() throws Exception {
        Level fakeLevel = allocateLevelWithRegistryAccess();
        assumeAllocationSucceeded(fakeLevel);

        var proc = new PatternProcessor();
        var vars = new StubVariableProvider().with("header", "hexcasting.page.101.4.header");
        proc.setup(fakeLevel, vars);

        IVariable out = proc.process(null, "translation_key");
        assertNotNull(out);
        assertEquals("hexcasting.page.101.4.header", out.asString(),
            "header branch must pass its value through unchanged");
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Allocate an uninitialised {@link ServerLevel} and write {@link RegistryAccess#EMPTY}
     * into {@code Level.registryAccess}. Returns null if the allocation path fails
     * (some JVM / Unsafe variants refuse abstract ancestors or final-field writes);
     * callers fall back to {@link #assumeAllocationSucceeded} to skip gracefully.
     * <p>
     * Uses {@code sun.misc.Unsafe} via reflection — {@code jdk.internal.misc.Unsafe}
     * is not accessible on JDK 21 without {@code --add-opens}, but {@code sun.misc.Unsafe}
     * remains a shim on top and is still accessible.
     */
    private static Level allocateLevelWithRegistryAccess() {
        try {
            Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeCls.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);

            // sun.misc.Unsafe.allocateInstance(Class) — allocates memory and zeros
            // fields, bypassing the constructor. Works on concrete classes; an abstract
            // class throws InstantiationException.
            var allocate = unsafeCls.getMethod("allocateInstance", Class.class);
            Object level = allocate.invoke(unsafe, ServerLevel.class);
            if (!(level instanceof Level)) return null;

            // Level.registryAccess is private final — reflection's setAccessible clears
            // the access check, but on JDK 17+ writing a final field of a non-static
            // class instance requires Unsafe.putObject with the field offset.
            Field registryAccess = Level.class.getDeclaredField("registryAccess");
            registryAccess.setAccessible(true);
            var objectFieldOffset = unsafeCls.getMethod("objectFieldOffset", Field.class);
            long offset = (long) objectFieldOffset.invoke(unsafe, registryAccess);
            var putObject = unsafeCls.getMethod("putObject",
                Object.class, long.class, Object.class);
            putObject.invoke(unsafe, level, offset, RegistryAccess.EMPTY);
            return (Level) level;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * JUnit 5.6 doesn't have {@code Assumptions.abort} in its common entry form; we use
     * {@code assumeTrue} via the standard API to skip when allocation is unavailable.
     * Wrapping as a helper so the call-site reads like documentation.
     */
    private static void assumeAllocationSucceeded(Level level) {
        org.junit.jupiter.api.Assumptions.assumeTrue(level != null,
            "Could not allocate uninitialised Level via sun.misc.Unsafe — "
                + "skipping; this path works on HotSpot JDK 21 but may fail on other JVMs.");
    }

    /**
     * Set {@code PatternProcessor.translationKey} directly. setup() normally populates
     * it — tests that want to exercise only process() use this to stay independent.
     */
    private static void setTranslationKey(PatternProcessor proc, String key) throws Exception {
        Field f = PatternProcessor.class.getDeclaredField("translationKey");
        f.setAccessible(true);
        f.set(proc, key);
    }

    /**
     * Minimal {@link IVariableProvider} subclass. Patchouli's real impl pulls vars from
     * a JsonObject-backed map and threads the {@link HolderLookup.Provider} through
     * {@code IVariable.wrap(elem, registries)} — we stub both sides.
     */
    private static final class StubVariableProvider implements IVariableProvider {
        private final Map<String, String> kvs = new HashMap<>();

        StubVariableProvider with(String k, String v) {
            kvs.put(k, v);
            return this;
        }

        @Override
        public IVariable get(String key, HolderLookup.Provider registries) {
            HolderLookup.Provider r = registries != null ? registries : RegistryAccess.EMPTY;
            String v = kvs.get(key);
            if (v == null) return IVariable.wrap(JsonNull.INSTANCE, r);
            return IVariable.wrap(new JsonPrimitive(v), r);
        }

        @Override
        public boolean has(String key) {
            return kvs.containsKey(key);
        }
    }
}
