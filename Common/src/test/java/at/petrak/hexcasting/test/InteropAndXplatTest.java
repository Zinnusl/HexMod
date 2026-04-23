package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.addldata.ADHexHolder;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.addldata.ADVariantItem;
import at.petrak.hexcasting.interop.HexInterop;
import at.petrak.hexcasting.interop.pehkui.PehkuiInterop;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import at.petrak.hexcasting.xplat.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Light-touch coverage for a cluster of interop/xplat/addldata surfaces that get loaded early
 * by {@code HexInterop.init}, the {@code IXplatAbstractions} stub, and hex items that expose
 * media/iota/hex/variant semantics. None of these types hit network or world state, so they're
 * all safe to exercise headless.
 *
 * <p>Why bother with "just check the method exists" style tests: the 1.21 port changed the
 * Patchouli {@code IComponentProcessor} contract (adding a {@code Level} param to both setup and
 * process) and the {@code IXplatAbstractions.isModPresent} boolean result is the single gate
 * that keeps {@link PehkuiInterop#isActive()} stable when Pehkui isn't installed. Regressions in
 * either would go undetected without a source-level assertion because the affected code paths
 * only run in a real Minecraft client.
 */
public final class InteropAndXplatTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    // ---- PehkuiInterop ----

    /**
     * {@link StubXplatAbstractions#isModPresent(String)} returns false for every mod id, so
     * {@link PehkuiInterop#isActive()} must also return false. If that ever inverts (e.g. someone
     * flips the negation by accident), every Pehkui-gated code path would fire in environments
     * where Pehkui isn't installed — guaranteed crash on the first pehkui.api import.
     */
    @Test
    public void pehkuiIsActiveIsFalseWhenStubSaysModAbsent() {
        assertFalse(IXplatAbstractions.INSTANCE.isModPresent(HexInterop.PEHKUI_ID),
            "stub xplat must report pehkui as not-present");
        assertFalse(PehkuiInterop.isActive(),
            "isActive() delegates to isModPresent — must be false under the stub");
    }

    @Test
    public void pehkuiApiAbstractionInterfaceIsLoadableAndShapedRight() {
        // ApiAbstraction is a plain SAM-ish interface used by whichever platform loader hooks
        // Pehkui. It has to stay a pure interface with exactly two abstract methods
        // (getScale, setScale) — the platform impls wire those to the real Pehkui ScaleData API.
        Class<?> cls = PehkuiInterop.ApiAbstraction.class;
        assertTrue(cls.isInterface(), "ApiAbstraction must be an interface");
        assertEquals(PehkuiInterop.class, cls.getEnclosingClass(),
            "ApiAbstraction is nested inside PehkuiInterop");

        long abstractMethodCount = Arrays.stream(cls.getDeclaredMethods())
            .filter(m -> Modifier.isAbstract(m.getModifiers()))
            .count();
        assertEquals(2, abstractMethodCount,
            "ApiAbstraction should define exactly getScale + setScale abstract methods");

        assertDoesNotThrow(() -> cls.getDeclaredMethod("getScale", net.minecraft.world.entity.Entity.class),
            "getScale(Entity) must exist");
        assertDoesNotThrow(() -> cls.getDeclaredMethod("setScale",
                net.minecraft.world.entity.Entity.class, float.class),
            "setScale(Entity, float) must exist");
    }

    @Test
    public void pehkuiInteropInitIsCallableAndHarmless() {
        // Current init() is intentionally a no-op placeholder — calling it must not throw or
        // leak state to IXplatAbstractions. If someone adds side effects later, this test at
        // least flags that the no-op contract broke.
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) PehkuiInterop::init);
    }

    // ---- BrainsweepProcessor (patchouli) ----

    /**
     * {@code BrainsweepProcessor} implements {@code vazkii.patchouli.api.IComponentProcessor},
     * which is not on the test classpath (patchouli is {@code compileOnly} in Common). Loading
     * the class triggers resolution of that super-interface and fails with
     * {@link NoClassDefFoundError}. We still want a canary for the 1.21 signature change
     * (setup/process gained a {@code Level} parameter) — the next best thing is to inspect the
     * class file bytes directly and assert the method descriptors include {@code Level}.
     *
     * <p>The class file lives on the test resource classpath at
     * {@code at/petrak/hexcasting/interop/patchouli/BrainsweepProcessor.class} once Common is
     * compiled. Walking the constant pool for the method descriptors is overkill, so we just
     * scan the bytes for the UTF8 descriptor fragments we expect. This is fragile-ish but
     * catches the exact regression the port introduced.
     */
    @Test
    public void brainsweepProcessorClassFileCarriesLevelInSignatures() throws Exception {
        byte[] bytes;
        try (var in = InteropAndXplatTest.class.getClassLoader().getResourceAsStream(
            "at/petrak/hexcasting/interop/patchouli/BrainsweepProcessor.class")) {
            assertNotNull(in,
                "BrainsweepProcessor.class must be on the classpath — did Common:compileJava run?");
            bytes = in.readAllBytes();
        }

        String asString = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        // 1.21 signatures after the port:
        //   setup(Level, IVariableProvider)   -> (Lnet/minecraft/world/level/Level;Lvazkii/patchouli/api/IVariableProvider;)V
        //   process(Level, String)            -> (Lnet/minecraft/world/level/Level;Ljava/lang/String;)Lvazkii/patchouli/api/IVariable;
        assertTrue(asString.contains("(Lnet/minecraft/world/level/Level;Lvazkii/patchouli/api/IVariableProvider;)V"),
            "setup(Level, IVariableProvider) descriptor must be in the class file");
        assertTrue(asString.contains("(Lnet/minecraft/world/level/Level;Ljava/lang/String;)Lvazkii/patchouli/api/IVariable;"),
            "process(Level, String) descriptor must be in the class file");

        // Also: the class should declare itself as implementing IComponentProcessor. The super
        // interface name shows up as a UTF8 constant-pool entry.
        assertTrue(asString.contains("vazkii/patchouli/api/IComponentProcessor"),
            "class file must reference IComponentProcessor super-interface");
    }

    // ---- Platform enum ----

    @Test
    public void platformEnumHasExactlyTheTwoKnownValues() {
        // Platform drives per-loader branches all over HexInterop. Adding a third value without
        // updating those branches would leave code dead; losing one would break dispatch. Pin it.
        Platform[] values = Platform.values();
        assertEquals(2, values.length, "Platform has exactly two values");
        assertArrayEquals(new Platform[]{Platform.FORGE, Platform.FABRIC}, values,
            "order is FORGE then FABRIC — branches in HexInterop.initPatchouli rely on this");
    }

    @Test
    public void platformValueOfRoundTripsByName() {
        // Enum.valueOf is what any "from string" caller (e.g. config readers) uses. Round-trip
        // by name must be a stable identity.
        for (Platform p : Platform.values()) {
            assertSame(p, Platform.valueOf(p.name()),
                "Platform.valueOf(" + p.name() + ") must equal the original");
        }
    }

    @Test
    public void platformValueOfRejectsUnknown() {
        // Enum.valueOf throws IllegalArgumentException for names outside the enum — a useful
        // contract for config validation code that tries to parse user input.
        assertThrows(IllegalArgumentException.class, () -> Platform.valueOf("QUILT"));
        assertThrows(IllegalArgumentException.class, () -> Platform.valueOf("forge"),
            "valueOf is case-sensitive");
    }

    @Test
    public void platformNamesAreStable() {
        // The enum names leak into mod-compat branches and (historically) into logs. Pin them.
        assertEquals("FORGE", Platform.FORGE.name());
        assertEquals("FABRIC", Platform.FABRIC.name());
    }

    // ---- addldata interfaces ----

    @Test
    public void adMediaHolderIsInterfaceWithExpectedAbstractSurface() throws NoSuchMethodException {
        Class<?> cls = ADMediaHolder.class;
        assertTrue(cls.isInterface(), "ADMediaHolder must be an interface");

        // Abstract methods: getMedia/getMaxMedia/setMedia + canRecharge/canProvide/
        // getConsumptionPriority/canConstructBattery. withdrawMedia/insertMedia are defaults.
        assertDoesNotThrow(() -> cls.getDeclaredMethod("getMedia"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("getMaxMedia"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("setMedia", long.class));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("canRecharge"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("canProvide"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("getConsumptionPriority"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("canConstructBattery"));

        // Default methods live on the interface too; they must stay default so subclasses can
        // opt out of overriding withdraw/insert when the getMedia/setMedia combo suffices.
        Method withdraw = cls.getDeclaredMethod("withdrawMedia", long.class, boolean.class);
        assertTrue(withdraw.isDefault(),
            "withdrawMedia must remain a default method — hex items rely on the default behaviour");
        Method insert = cls.getDeclaredMethod("insertMedia", long.class, boolean.class);
        assertTrue(insert.isDefault(), "insertMedia must remain a default method");
    }

    @Test
    public void adMediaHolderPriorityConstantsAreOrdered() {
        // The consumption-priority order decides which item hex drains first. Pin the order —
        // swapping two constants would change in-game behaviour for every caster.
        assertTrue(ADMediaHolder.QUENCHED_ALLAY_PRIORITY < ADMediaHolder.QUENCHED_SHARD_PRIORITY);
        assertTrue(ADMediaHolder.QUENCHED_SHARD_PRIORITY < ADMediaHolder.CHARGED_AMETHYST_PRIORITY);
        assertTrue(ADMediaHolder.CHARGED_AMETHYST_PRIORITY < ADMediaHolder.AMETHYST_SHARD_PRIORITY);
        assertTrue(ADMediaHolder.AMETHYST_SHARD_PRIORITY < ADMediaHolder.AMETHYST_DUST_PRIORITY);
        assertTrue(ADMediaHolder.AMETHYST_DUST_PRIORITY < ADMediaHolder.BATTERY_PRIORITY);
    }

    @Test
    public void adMediaHolderDefaultWithdrawInsertRoundtrips() {
        // Exercise the default impl against a tiny in-memory holder. Contract: withdraw(-1, true)
        // reports the current media without mutating; withdraw(-1, false) drains to zero;
        // insert(-1, false) fills to max.
        var holder = new InMemoryMediaHolder(50, 100);
        assertEquals(50, holder.withdrawMedia(-1, true), "withdraw(-1, simulate) reports current");
        assertEquals(50, holder.getMedia(), "simulate-only withdraw must not mutate");
        assertEquals(50, holder.withdrawMedia(-1, false), "withdraw(-1, commit) drains to zero");
        assertEquals(0, holder.getMedia(), "post-withdraw state is empty");
        assertEquals(100, holder.insertMedia(-1, false), "insert(-1, commit) fills to max");
        assertEquals(100, holder.getMedia(), "post-insert state is full");
    }

    @Test
    public void adIotaHolderIsInterfaceWithExpectedAbstractSurface() throws NoSuchMethodException {
        Class<?> cls = ADIotaHolder.class;
        assertTrue(cls.isInterface(), "ADIotaHolder is an interface");

        assertDoesNotThrow(() -> cls.getDeclaredMethod("readIotaTag"),
            "readIotaTag is the core abstract method — delegates do the CompoundTag lift");
        assertDoesNotThrow(() -> cls.getDeclaredMethod("writeIota",
                at.petrak.hexcasting.api.casting.iota.Iota.class, boolean.class),
            "writeIota(Iota, simulate) must keep its pair-param shape");
        assertDoesNotThrow(() -> cls.getDeclaredMethod("writeable"),
            "writeable gates whether the holder accepts writeIota at all");

        // readIota/emptyIota are default convenience methods.
        Method readIota = cls.getDeclaredMethod("readIota", net.minecraft.server.level.ServerLevel.class);
        assertTrue(readIota.isDefault(), "readIota(ServerLevel) stays default");
        Method emptyIota = cls.getDeclaredMethod("emptyIota");
        assertTrue(emptyIota.isDefault(), "emptyIota stays default");
    }

    @Test
    public void adHexHolderIsInterfaceWithExpectedAbstractSurface() {
        Class<?> cls = ADHexHolder.class;
        assertTrue(cls.isInterface());

        assertDoesNotThrow(() -> cls.getDeclaredMethod("canDrawMediaFromInventory"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("hasHex"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("getHex",
            net.minecraft.server.level.ServerLevel.class));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("writeHex",
            java.util.List.class,
            at.petrak.hexcasting.api.pigment.FrozenPigment.class,
            long.class));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("clearHex"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("getPigment"));
    }

    @Test
    public void adVariantItemIsInterfaceWithThreeMethods() {
        Class<?> cls = ADVariantItem.class;
        assertTrue(cls.isInterface());

        // Exactly three abstract methods — simple variant-count + getter/setter trio.
        long abstractMethodCount = Arrays.stream(cls.getDeclaredMethods())
            .filter(m -> Modifier.isAbstract(m.getModifiers()))
            .count();
        assertEquals(3, abstractMethodCount, "ADVariantItem: numVariants + getVariant + setVariant");

        assertDoesNotThrow(() -> cls.getDeclaredMethod("numVariants"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("getVariant"));
        assertDoesNotThrow(() -> cls.getDeclaredMethod("setVariant", int.class));
    }

    // ---- helpers ----

    /**
     * Minimal in-memory {@link ADMediaHolder} used to exercise the default methods. Only the
     * fields actually touched by withdraw/insert defaults are implemented.
     */
    private static final class InMemoryMediaHolder implements ADMediaHolder {
        private long media;
        private final long max;

        InMemoryMediaHolder(long initial, long max) {
            this.media = initial;
            this.max = max;
        }

        @Override public long getMedia() { return media; }
        @Override public long getMaxMedia() { return max; }
        @Override public void setMedia(long media) { this.media = media; }
        @Override public boolean canRecharge() { return true; }
        @Override public boolean canProvide() { return true; }
        @Override public int getConsumptionPriority() { return 0; }
        @Override public boolean canConstructBattery() { return false; }
    }
}
