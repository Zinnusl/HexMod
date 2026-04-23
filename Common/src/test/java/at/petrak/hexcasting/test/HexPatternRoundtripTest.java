package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HexPattern is the core casting primitive — if its NBT round-trip regresses under
 * 1.21's serialization API, every spell stored on a slate / scroll / cypher
 * corrupts on save/load. These tests exercise the parse-from-angles → serialize →
 * fromNBT loop and the HexCoord angle math that pattern recognition depends on.
 */
public final class HexPatternRoundtripTest {
    @BeforeAll
    public static void bootstrap() {
        // Needed so Component.Serializer, registry lookups, etc. work.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void emptyPatternRoundtrips() {
        HexPattern pat = HexPattern.fromAngles("", HexDir.EAST);
        HexPattern rt = HexPattern.fromNBT(pat.serializeToNBT());
        assertEquals(pat, rt, "empty-signature pattern must survive round-trip");
    }

    @Test
    public void simpleSignatureRoundtrips() {
        // "qqq" is the signature for mind's reflection (player motion) — widely used.
        HexPattern pat = HexPattern.fromAngles("qqq", HexDir.EAST);
        HexPattern rt = HexPattern.fromNBT(pat.serializeToNBT());
        assertEquals(pat, rt, "qqq pattern must survive round-trip");
        assertEquals(HexDir.EAST, rt.getStartDir(), "start dir preserved");
        assertEquals(3, rt.getAngles().size(), "angle count preserved");
    }

    @Test
    public void complexSignatureRoundtrips() {
        // A non-trivial valid pattern — the exact signature vanilla recognises as
        // "mind's reflection" uses an open spiral. "wqaqw" traces a zig-zag with
        // four distinct angles, stressing the enum serialization without tripping
        // the overlap check.
        HexPattern pat = HexPattern.fromAngles("wqaqw", HexDir.SOUTH_EAST);
        HexPattern rt = HexPattern.fromNBT(pat.serializeToNBT());
        assertEquals(pat, rt, "mixed-angle pattern must survive round-trip");
    }

    @Test
    public void allStartDirsRoundtrip() {
        for (HexDir dir : HexDir.values()) {
            HexPattern pat = HexPattern.fromAngles("ea", dir);
            HexPattern rt = HexPattern.fromNBT(pat.serializeToNBT());
            assertEquals(pat, rt, "pattern with startDir=" + dir + " must round-trip");
        }
    }

    @Test
    public void hexCoordBasicArithmetic() {
        // Regression guard on HexCoord axial math — if hex-grid rotation regresses,
        // spiral pattern recognition breaks even when individual signatures parse.
        HexCoord a = new HexCoord(2, 1);
        HexCoord b = new HexCoord(-1, 3);
        assertEquals(new HexCoord(1, 4), a.plus(b), "hex-coord addition");
        assertEquals(new HexCoord(3, -2), a.minus(b), "hex-coord subtraction");
    }

    @Test
    public void positionsMatchForEqualPatterns() {
        HexPattern a = HexPattern.fromAngles("wqaq", HexDir.EAST);
        HexPattern b = HexPattern.fromAngles("wqaq", HexDir.EAST);
        // Different instances, same logical pattern — both positions + angle list equal.
        assertEquals(a.getAngles(), b.getAngles(), "signature parity");
        assertIterableEquals(a.positions(HexCoord.getOrigin()),
                             b.positions(HexCoord.getOrigin()),
                             "trace positions parity");
    }
}
