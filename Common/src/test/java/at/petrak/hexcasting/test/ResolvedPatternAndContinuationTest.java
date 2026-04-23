package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.FrameFinishEval;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * More VM-state types that ride on saved-world NBT:
 * <ul>
 *   <li>{@link ResolvedPattern} — a pattern entry in a player's staff UI, persisted across relogs.
 *       Carries the pattern, origin, and resolution state.</li>
 *   <li>{@link ResolvedPatternType} — color + success info for UI rendering. Lowercase name is
 *       used as the NBT "Valid" tag value, so casing drift breaks load.</li>
 *   <li>{@link SpellContinuation} — stack of continuation frames. Push/pop are the backbone of
 *       Hermes/introspection semantics.</li>
 *   <li>{@link CastResult} — the result a CastingVM.executeInner produces; smoke-test the record
 *       constructor takes non-null required fields and allows null newData.</li>
 * </ul>
 */
public final class ResolvedPatternAndContinuationTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void resolvedPatternRoundtripsNBT() {
        var pattern = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var origin = new HexCoord(3, -1);
        var original = new ResolvedPattern(pattern, origin, ResolvedPatternType.EVALUATED);

        CompoundTag tag = original.serializeToNBT();
        var back = ResolvedPattern.fromNBT(tag);

        assertEquals(pattern.anglesSignature(), back.getPattern().anglesSignature(),
            "pattern angles survive");
        assertEquals(pattern.getStartDir(), back.getPattern().getStartDir(),
            "pattern startDir survives");
        assertEquals(origin, back.getOrigin(), "origin coords preserved");
        assertEquals(ResolvedPatternType.EVALUATED, back.getType(), "type preserved");
    }

    @Test
    public void resolvedPatternUsesLowercaseNBTName() {
        // "Valid" field stores the enum name lowercased — this is how old save files identify
        // the resolution type. Breaking the lowercase convention would make every previously
        // saved pattern look INVALID on reload.
        var pat = HexPattern.fromAngles("aa", HexDir.EAST);
        var rp = new ResolvedPattern(pat, HexCoord.getOrigin(), ResolvedPatternType.ERRORED);
        var tag = rp.serializeToNBT();
        assertEquals("errored", tag.getString("Valid"),
            "ResolvedPatternType.ERRORED -> \"errored\" (lowercased)");
    }

    @Test
    public void resolvedPatternTypeFromStringRoundtrips() {
        // Every enum value → its lowercase name → back via fromString.
        for (var type : ResolvedPatternType.values()) {
            var asString = type.name().toLowerCase();
            var back = ResolvedPatternType.fromString(asString);
            assertEquals(type, back, "fromString for " + type);
        }
    }

    @Test
    public void resolvedPatternTypeColorsAreValid() {
        // Each type exposes a color + fadeColor for UI rendering. Spot-check they're in
        // reasonable RGB range (the 0xRRGGBB form, no alpha expected).
        for (var type : ResolvedPatternType.values()) {
            assertTrue(type.getColor() >= 0 && type.getColor() <= 0xffffff,
                () -> type + ": color in 24-bit range");
            assertTrue(type.getFadeColor() >= 0 && type.getFadeColor() <= 0xffffff,
                () -> type + ": fadeColor in 24-bit range");
        }

        // Success flags have to be right: EVALUATED and ESCAPED are success; ERRORED/INVALID are not.
        assertTrue(ResolvedPatternType.EVALUATED.getSuccess(), "EVALUATED is a success");
        assertTrue(ResolvedPatternType.ESCAPED.getSuccess(), "ESCAPED is a success");
        assertFalse(ResolvedPatternType.ERRORED.getSuccess(), "ERRORED is not a success");
        assertFalse(ResolvedPatternType.INVALID.getSuccess(), "INVALID is not a success");
        assertFalse(ResolvedPatternType.UNRESOLVED.getSuccess(), "UNRESOLVED is not a success");
    }

    @Test
    public void spellContinuationDoneIsSingleton() {
        assertSame(SpellContinuation.Done.INSTANCE, SpellContinuation.Done.INSTANCE,
            "Done is an object — same singleton each access");
    }

    @Test
    public void spellContinuationPushFramePreservesOrder() {
        // pushFrame prepends; traversing via NotDone.next yields frames in reverse-push order
        // (most recent first). This is the stack semantics every introspection op relies on.
        SpellContinuation s = SpellContinuation.Done.INSTANCE;
        s = s.pushFrame(FrameFinishEval.INSTANCE);
        s = s.pushFrame(FrameFinishEval.INSTANCE);
        s = s.pushFrame(FrameFinishEval.INSTANCE);

        assertTrue(s instanceof SpellContinuation.NotDone,
            "after 3 pushes, continuation is NotDone");

        int count = 0;
        SpellContinuation cursor = s;
        while (cursor instanceof SpellContinuation.NotDone nd) {
            count++;
            cursor = nd.getNext();
        }
        assertEquals(3, count, "3 pushes = 3 NotDone frames");
        assertSame(SpellContinuation.Done.INSTANCE, cursor, "bottom of stack is Done");
    }

    @Test
    public void spellContinuationGetNBTFramesReturnsEachFrame() {
        // getNBTFrames returns one CompoundTag per frame — used for serializing the whole stack.
        // If push-order regression breaks, serialized stack would be in the wrong order on load.
        var s = SpellContinuation.Done.INSTANCE.pushFrame(FrameFinishEval.INSTANCE)
            .pushFrame(FrameFinishEval.INSTANCE);
        var frames = s.getNBTFrames();
        assertEquals(2, frames.size(), "two frames pushed, two emitted");
        for (var frame : frames) {
            assertTrue(frame.contains("hexcasting:type"),
                "each frame carries the hex type key");
            assertEquals("hexcasting:end", frame.getString("hexcasting:type"),
                "both frames are FrameFinishEval (hex:end)");
        }
    }

    @Test
    public void castResultAllowsNullNewData() {
        // newData is nullable — many mishaps produce a CastResult with no new image. This is
        // the contract CastingVM.handleResult relies on when short-circuiting on error.
        var result = new CastResult(
            new NullIota(),
            SpellContinuation.Done.INSTANCE,
            null,                               // newData
            List.of(),                          // sideEffects
            ResolvedPatternType.EVALUATED,
            HexEvalSounds.NOTHING
        );
        assertNull(result.getNewData(), "null newData permitted");
        assertSame(SpellContinuation.Done.INSTANCE, result.getContinuation());
        assertEquals(ResolvedPatternType.EVALUATED, result.getResolutionType());
    }

    @Test
    public void castResultPreservesSideEffectsList() {
        // Data-class copy: mutating the original list shouldn't retroactively change the
        // CastResult (Kotlin data classes keep the reference but conventionally lists are
        // immutable lists like List.of).
        var result = new CastResult(
            new NullIota(),
            SpellContinuation.Done.INSTANCE,
            new CastingImage(),
            List.of(),
            ResolvedPatternType.EVALUATED,
            HexEvalSounds.NOTHING
        );
        assertNotNull(result.getSideEffects(), "side-effects list non-null");
        assertTrue(result.getSideEffects().isEmpty(), "side-effects list empty when passed empty");
    }
}
