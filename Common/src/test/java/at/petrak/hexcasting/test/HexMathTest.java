package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HexDir / HexAngle / HexCoord are the geometric primitives under HexPattern. All pattern
 * resolution — dispatch, special handlers, circle traversal — reads off of them. They're
 * pure-value types so they're cheap to unit-test, but they're also the hardest to notice if they
 * silently break: rotation going "the wrong way" by one notch would break every angle-dependent
 * spell without any obvious crash.
 */
public final class HexMathTest {
    @BeforeAll
    public static void bootstrap() {
        // Doesn't actually need bootstrap, but keeps the pattern consistent with other tests.
        TestBootstrap.init();
    }

    @Test
    public void hexDirRotationIsClosedAndInverse() {
        // Rotating by LEFT then RIGHT returns to the original — fundamental invariant used by
        // the mask special handler to detect dip segments.
        for (var dir : HexDir.values()) {
            assertEquals(dir, dir.rotatedBy(HexAngle.LEFT).rotatedBy(HexAngle.RIGHT),
                "LEFT then RIGHT is identity");
            assertEquals(dir, dir.rotatedBy(HexAngle.FORWARD),
                "FORWARD rotation is identity");
            assertEquals(dir, dir.rotatedBy(HexAngle.BACK).rotatedBy(HexAngle.BACK),
                "BACK twice is identity");
        }
    }

    @Test
    public void hexDirFullRotationReturnsToStart() {
        // Six RIGHT rotations = full circle (the hex is 6-sided).
        var dir = HexDir.EAST;
        for (int i = 0; i < 6; i++) dir = dir.rotatedBy(HexAngle.RIGHT);
        assertEquals(HexDir.EAST, dir, "6 RIGHT rotations cycles back");
    }

    @Test
    public void hexDirAngleFromReflexive() {
        // angleFrom(self) should always be FORWARD — if this breaks, every dispatch table that
        // compares pattern directions against each other misbehaves.
        for (var dir : HexDir.values()) {
            assertEquals(HexAngle.FORWARD, dir.angleFrom(dir), "angleFrom(self) is FORWARD");
        }
    }

    @Test
    public void hexDirAsDeltaRoundTrip() {
        // Each HexDir has a corresponding HexCoord delta; stepping ORIGIN by the delta and
        // asking immediateDelta gives back the original direction.
        for (var dir : HexDir.values()) {
            var stepped = HexCoord.getOrigin().shiftedBy(dir);
            assertEquals(dir, HexCoord.getOrigin().immediateDelta(stepped),
                "immediateDelta of asDelta returns original dir (" + dir + ")");
        }
    }

    @Test
    public void hexCoordDistanceSymmetric() {
        // distance(a, b) == distance(b, a) — basic metric property.
        var a = new HexCoord(2, -1);
        var b = new HexCoord(-3, 2);
        assertEquals(a.distanceTo(b), b.distanceTo(a),
            "distance is symmetric");
        assertEquals(0, a.distanceTo(a), "distance to self is 0");
    }

    @Test
    public void hexCoordArithmeticIsInvertible() {
        var a = new HexCoord(3, -2);
        var d = HexDir.NORTH_EAST;
        var stepped = a.shiftedBy(d);
        assertEquals(a, stepped.delta(d.asDelta()),
            "stepping by a dir then subtracting the delta returns to origin");
    }

    @Test
    public void hexCoordRangeAroundMatchesExpectedSize() {
        // A radius-N ring has exactly 1 + 3*N*(N+1) cells (centered hexagon).
        // radius 0: just the center.
        // radius 1: 7 cells total (center + 6 neighbors).
        // radius 2: 19 cells (1 + 6 + 12).
        Set<HexCoord> r0 = collectToSet(HexCoord.getOrigin().rangeAround(0));
        assertEquals(1, r0.size(), "radius 0: 1 cell");

        Set<HexCoord> r1 = collectToSet(HexCoord.getOrigin().rangeAround(1));
        assertEquals(7, r1.size(), "radius 1: 7 cells");

        Set<HexCoord> r2 = collectToSet(HexCoord.getOrigin().rangeAround(2));
        assertEquals(19, r2.size(), "radius 2: 19 cells");

        // All radius-0 elements are in radius-1 (subset property).
        assertTrue(r1.containsAll(r0), "larger radius is a superset");
    }

    @Test
    public void hexAngleFromCharCoversAllLetters() {
        // The special handlers parse angle letters off the pattern string. Missing one letter
        // would make entire spell signatures unrecognizable.
        assertEquals(HexAngle.FORWARD, HexAngle.Companion.fromChar('w'));
        assertEquals(HexAngle.RIGHT, HexAngle.Companion.fromChar('e'));
        assertEquals(HexAngle.RIGHT_BACK, HexAngle.Companion.fromChar('d'));
        assertEquals(HexAngle.BACK, HexAngle.Companion.fromChar('s'));
        assertEquals(HexAngle.LEFT_BACK, HexAngle.Companion.fromChar('a'));
        assertEquals(HexAngle.LEFT, HexAngle.Companion.fromChar('q'));
        assertNull(HexAngle.Companion.fromChar('x'), "unknown letter -> null");
    }

    @Test
    public void patternDirectionsHaveMatchingCount() {
        // HexPattern.directions() returns a list whose first element is the startDir, and each
        // subsequent entry is the direction after rotating by the i-th angle. For a k-angle
        // signature, directions() has k+1 entries.
        var pat = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        assertEquals(pat.getAngles().size() + 1, pat.directions().size(),
            "directions().size = angles.size + 1 (startDir + after each angle)");
        assertEquals(HexDir.NORTH_EAST, pat.directions().get(0),
            "first direction is startDir");
    }

    @Test
    public void patternRotationsAreNotEqualUnderSignature() {
        // Two patterns with same signature but different startDir are distinct — the equality
        // contract for PatternIota relies on this. If HexPattern.equals ignored startDir, spells
        // that start from different orientations would alias to each other.
        var a = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var b = HexPattern.fromAngles("qaq", HexDir.EAST);
        assertNotEquals(a, b,
            "same angles, different startDir -> different pattern");
    }

    private static Set<HexCoord> collectToSet(java.util.Iterator<HexCoord> it) {
        var set = new HashSet<HexCoord>();
        while (it.hasNext()) set.add(it.next());
        return set;
    }
}
