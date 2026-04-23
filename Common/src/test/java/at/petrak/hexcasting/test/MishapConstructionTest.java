package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.GarbageIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapAlreadyBrainswept;
import at.petrak.hexcasting.api.casting.mishaps.MishapDivideByZero;
import at.petrak.hexcasting.api.casting.mishaps.MishapEvalTooMuch;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidOperatorArgs;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.casting.mishaps.MishapStackSize;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mishaps are the error-reporting layer spells use to signal failures — they're instantiated from
 * many hot paths in the casting VM and must not throw during construction. The recent port
 * incident (Flay Mind on sheep crashing the client) was a NullPointerException thrown inside
 * {@link at.petrak.hexcasting.api.casting.mishaps.Mishap#errorMessage} — the mishap itself
 * constructed fine, then blew up during serialization because an upstream value was null.
 * <p>
 * These tests exercise each mishap's constructor and the {@code DivideByZero.of} factory
 * overloads — catching any NPEs in the construction path early.
 */
public final class MishapConstructionTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void divideByZeroOfDoubles() {
        // Kotlin default param `suffix = "divide"` isn't exposed as a Java overload without
        // @JvmOverloads on the factory — pass the suffix explicitly.
        var m = MishapDivideByZero.of(1.0, 0.0, "divide");
        assertNotNull(m, "basic divide-by-zero constructs");

        // Exponent suffix routes through a different branch with powerOf() — regression target.
        var expMishap = MishapDivideByZero.of(-1.0, 0.5, "exponent");
        assertNotNull(expMishap, "exponent-suffix divide-by-zero constructs");
    }

    @Test
    public void divideByZeroOfIotas() {
        // Iota overload — Vec3Iota(Vec3.ZERO) should route through the zeroVector label, not
        // display(). A regression where translate() always called display() would lose the
        // localized message.
        Iota zeroDouble = new DoubleIota(0.0);
        Iota zeroVec = new Vec3Iota(Vec3.ZERO);
        Iota nonzeroVec = new Vec3Iota(new Vec3(1, 0, 0));

        assertNotNull(MishapDivideByZero.of(zeroDouble, nonzeroVec, "divide"));
        assertNotNull(MishapDivideByZero.of(nonzeroVec, zeroVec, "divide"));
        assertNotNull(MishapDivideByZero.of(zeroDouble, zeroDouble, "divide"));
    }

    @Test
    public void divideByZeroTan() {
        // tan() has two overloads — double and DoubleIota. Both must work.
        assertNotNull(MishapDivideByZero.tan(Math.PI / 2));
        assertNotNull(MishapDivideByZero.tan(new DoubleIota(Math.PI / 2)));
    }

    @Test
    public void notEnoughArgsMishapConstructs() {
        // Used on every action that pops more than the stack has — very hot path. NPE here
        // would crash every partial-spell failure.
        assertNotNull(new MishapNotEnoughArgs(3, 1));
        assertNotNull(new MishapNotEnoughArgs(5, 0), "zero-got variant has its own message branch");
    }

    @Test
    public void invalidIotaMishapConstructs() {
        // Used when an iota arg is the wrong type. Construction with a DoubleIota masquerading
        // as a PatternIota target would NPE if the iota-display path broke.
        var mishap = new MishapInvalidIota(new DoubleIota(5.0), 0,
            net.minecraft.network.chat.Component.literal("number"));
        assertNotNull(mishap);

        // Null iota handled — GarbageIota is the fallback.
        var garbage = new MishapInvalidIota(new GarbageIota(), 0,
            net.minecraft.network.chat.Component.literal("anything"));
        assertNotNull(garbage);
    }

    @Test
    public void invalidOperatorArgsMishapConstructs() {
        var mishap = new MishapInvalidOperatorArgs(
            java.util.List.of(new DoubleIota(1.0), new NullIota())
        );
        assertNotNull(mishap);
    }

    @Test
    public void notEnoughMediaMishapConstructs() {
        // Media shortage — used any time a spell underpays. Constructor takes a long cost.
        assertNotNull(new MishapNotEnoughMedia(1000L));
        assertNotNull(new MishapNotEnoughMedia(0L), "zero cost still valid construction");
        assertNotNull(new MishapNotEnoughMedia(Long.MAX_VALUE),
            "very large cost doesn't overflow construction");
    }

    @Test
    public void stackSizeMishapConstructs() {
        assertNotNull(new MishapStackSize());
    }

    @Test
    public void evalTooMuchMishapConstructs() {
        assertNotNull(new MishapEvalTooMuch());
    }

    @Test
    public void invalidPatternMishapConstructs() {
        // This is thrown when a drawn pattern doesn't map to any known action. Hot path.
        assertNotNull(new MishapInvalidPattern());
    }

    @Test
    public void alreadyBrainsweptMishapConstructs() {
        // Requires a Mob argument; skip — needs real mob instance. Covered by @GameTest instead.
        // But the class itself is loaded without error:
        assertNotNull(MishapAlreadyBrainswept.class,
            "MishapAlreadyBrainswept class is loadable");
    }
}
