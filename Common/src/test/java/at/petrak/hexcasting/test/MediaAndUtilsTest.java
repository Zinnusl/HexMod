package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.utils.HexUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Media amount tuning and HexConfig defaults are load-bearing values that player-facing
 * balance depends on — a typo that changes DUST_UNIT would re-scale every amethyst drop and
 * every cost in the mod. The values are also referenced in data-pack generation, so a mismatch
 * against generated JSON would load-crash downstream.
 * <p>
 * Also covers a few {@link HexUtils} helpers that hot-path math routes through — {@code fixNAN}
 * turns NaN/Infinity into 0 and is what stops players crashing the client with division results.
 */
public final class MediaAndUtilsTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void mediaUnitsHaveStableMagnitudes() {
        // Exact values are the contract hex's data pack + recipe cost calculations assume. A
        // change here without coordinated data-pack regeneration would be catastrophic.
        assertEquals(10_000L, MediaConstants.DUST_UNIT, "DUST_UNIT");
        assertEquals(50_000L, MediaConstants.SHARD_UNIT, "SHARD_UNIT = 5 * DUST");
        assertEquals(100_000L, MediaConstants.CRYSTAL_UNIT, "CRYSTAL_UNIT = 10 * DUST");
        assertEquals(300_000L, MediaConstants.QUENCHED_SHARD_UNIT,
            "QUENCHED_SHARD_UNIT = 3 * CRYSTAL");
        assertEquals(1_200_000L, MediaConstants.QUENCHED_BLOCK_UNIT,
            "QUENCHED_BLOCK_UNIT = 4 * QUENCHED_SHARD");
    }

    @Test
    public void mediaUnitOrderingIsMonotonic() {
        // The media economy is structured as a progression: dust < shard < crystal < quenched.
        // A rebalance should preserve that invariant even if magnitudes drift.
        assertTrue(MediaConstants.DUST_UNIT < MediaConstants.SHARD_UNIT, "dust < shard");
        assertTrue(MediaConstants.SHARD_UNIT < MediaConstants.CRYSTAL_UNIT, "shard < crystal");
        assertTrue(MediaConstants.CRYSTAL_UNIT < MediaConstants.QUENCHED_SHARD_UNIT, "crystal < quenched shard");
        assertTrue(MediaConstants.QUENCHED_SHARD_UNIT < MediaConstants.QUENCHED_BLOCK_UNIT,
            "quenched shard < quenched block");
    }

    @Test
    public void hexConfigDefaultsMatchMediaConstants() {
        // HexConfig carries DEFAULT_* constants for the common config. They must agree with
        // MediaConstants — any drift means the config UI + docs are lying to players.
        assertEquals(MediaConstants.DUST_UNIT, HexConfig.CommonConfigAccess.DEFAULT_DUST_MEDIA_AMOUNT);
        assertEquals(MediaConstants.SHARD_UNIT, HexConfig.CommonConfigAccess.DEFAULT_SHARD_MEDIA_AMOUNT);
        assertEquals(MediaConstants.CRYSTAL_UNIT, HexConfig.CommonConfigAccess.DEFAULT_CHARGED_MEDIA_AMOUNT);
        // Media-to-health: 2 crystals per 20 HP = 10000 media per HP.
        assertEquals(2 * MediaConstants.CRYSTAL_UNIT / 20.0,
            HexConfig.CommonConfigAccess.DEFAULT_MEDIA_TO_HEALTH_RATE, 0.0,
            "media-to-health default");
    }

    @Test
    public void cooldownDefaultsAreSane() {
        // Cooldowns: cypher > trinket > artifact (rarer = shorter cooldown). If inverted, rare
        // trinkets become the worst-value item in the progression.
        assertTrue(HexConfig.CommonConfigAccess.DEFAULT_CYPHER_COOLDOWN
                > HexConfig.CommonConfigAccess.DEFAULT_TRINKET_COOLDOWN,
            "cypher cooldown > trinket cooldown");
        assertTrue(HexConfig.CommonConfigAccess.DEFAULT_TRINKET_COOLDOWN
                > HexConfig.CommonConfigAccess.DEFAULT_ARTIFACT_COOLDOWN,
            "trinket cooldown > artifact cooldown");
        assertTrue(HexConfig.CommonConfigAccess.DEFAULT_ARTIFACT_COOLDOWN > 0,
            "artifact cooldown is positive");
    }

    @Test
    public void fixNANScrubsFloatingPointGarbage() {
        // fixNAN is called on every DoubleIota read; it's the single point where we protect
        // against NaN/Inf contamination of the stack. Bug here would crash on the very next
        // comparison that expects a finite double.
        assertEquals(0.0, HexUtils.fixNAN(Double.NaN), 0.0, "NaN scrubbed to 0");
        assertEquals(0.0, HexUtils.fixNAN(Double.POSITIVE_INFINITY), 0.0, "+Inf scrubbed to 0");
        assertEquals(0.0, HexUtils.fixNAN(Double.NEGATIVE_INFINITY), 0.0, "-Inf scrubbed to 0");
        assertEquals(1.5, HexUtils.fixNAN(1.5), 0.0, "finite value passes through");
        assertEquals(-Math.PI, HexUtils.fixNAN(-Math.PI), 0.0, "negative finite passes through");
        assertEquals(0.0, HexUtils.fixNAN(0.0), 0.0, "zero passes through (not NaN)");
    }

    @Test
    public void errorColorIsHotPink() {
        // HexUtils.ERROR_COLOR is what the broken-iota tooltip uses — specifically chosen to be
        // garishly visible so players notice.
        assertEquals(0xff_f800f8, HexUtils.ERROR_COLOR,
            "ERROR_COLOR is the hot-pink we use on broken iota displays");
    }

    @Test
    public void pigmentCooldownsArePositive() {
        // Sanity: all cooldowns are strictly positive. A zero cooldown would allow spell spam.
        assertTrue(HexConfig.CommonConfigAccess.DEFAULT_CYPHER_COOLDOWN > 0);
        assertTrue(HexConfig.CommonConfigAccess.DEFAULT_TRINKET_COOLDOWN > 0);
        assertTrue(HexConfig.CommonConfigAccess.DEFAULT_ARTIFACT_COOLDOWN > 0);
    }
}
