package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.casting.mishaps.MishapStackSize;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for hot-path VM types:
 * <ul>
 *   <li>{@link PatternShapeMatch} — four-variant sealed hierarchy. Every drawn pattern flows
 *       through a resolver that returns one of these; a broken variant would make entire
 *       pattern classes unresolvable.</li>
 *   <li>{@link OperatorSideEffect} — each variant is constructed many times per cast. A null
 *       field in the constructor would NPE on every spell.</li>
 *   <li>{@link ParticleSpray} — factory methods (burst, cloud) + data-class construction are
 *       used by dozens of mishaps and operator side effects.</li>
 * </ul>
 */
public final class SideEffectAndShapeMatchTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void patternShapeMatchNothingIsConstructable() {
        // Nothing is the default when a pattern doesn't map to anything — it's hot-path.
        var m = new PatternShapeMatch.Nothing();
        assertNotNull(m);
        assertTrue(m instanceof PatternShapeMatch, "Nothing is-a PatternShapeMatch");
    }

    @Test
    public void patternShapeMatchNormalCarriesKey() {
        // Normal.key is what PlayerBasedCastEnv.getCostModifier extracts for tag lookup — broke
        // during the port (mishap NPE from null key). Guard the construction path.
        ResourceKey<net.minecraft.core.Registry<ActionRegistryEntry>> regKey =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "action"));
        ResourceKey<ActionRegistryEntry> actionKey = ResourceKey.create(regKey,
            ResourceLocation.fromNamespaceAndPath("hexcasting", "get_caster"));

        var m = new PatternShapeMatch.Normal(actionKey);
        assertSame(actionKey, m.key, "Normal.key preserved from constructor");
    }

    @Test
    public void patternShapeMatchPerWorldCarriesCertainty() {
        // PerWorld tracks "is this the right shape AND the correct per-world variant for this
        // specific server seed?" — the client always sees certain=false.
        ResourceKey<net.minecraft.core.Registry<ActionRegistryEntry>> regKey =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "action"));
        ResourceKey<ActionRegistryEntry> actionKey = ResourceKey.create(regKey,
            ResourceLocation.fromNamespaceAndPath("hexcasting", "craft/battery"));

        var certain = new PatternShapeMatch.PerWorld(actionKey, true);
        var uncertain = new PatternShapeMatch.PerWorld(actionKey, false);
        assertTrue(certain.certain);
        assertFalse(uncertain.certain);
        assertSame(actionKey, certain.key);
    }

    @Test
    public void operatorSideEffectConsumeMediaPreservesAmount() {
        // ConsumeMedia carries the cost for later extraction — field preservation is the
        // contract. Negative amounts should not be rejected here (they're validated downstream).
        var e = new OperatorSideEffect.ConsumeMedia(5000L);
        assertEquals(5000L, e.component1(), "Kotlin data-class component1() returns the amount");
        assertEquals(5000L, e.getAmount());

        var zero = new OperatorSideEffect.ConsumeMedia(0L);
        assertEquals(0L, zero.getAmount(), "zero is a valid 'free' side effect");
    }

    @Test
    public void operatorSideEffectDoMishapCarriesMishap() {
        // DoMishap wraps a Mishap instance + Context. The Mishap is what surfaces the localized
        // error to the player — losing it here means losing the error message entirely.
        Mishap m = new MishapNotEnoughArgs(3, 1);
        Mishap.Context ctx = new Mishap.Context(null, null);
        var e = new OperatorSideEffect.DoMishap(m, ctx);
        assertSame(m, e.getMishap(), "mishap reference preserved");
        assertSame(ctx, e.getErrorCtx(), "error context preserved");
    }

    @Test
    public void operatorSideEffectParticlesWrapsSpray() {
        var spray = new ParticleSpray(new Vec3(1, 2, 3), new Vec3(0, 1, 0), 0.1, 0.5, 30);
        var e = new OperatorSideEffect.Particles(spray);
        assertSame(spray, e.getSpray(), "particle spray reference preserved");
    }

    @Test
    public void operatorSideEffectRequiredEnlightenmentCarriesAwardStat() {
        // The awardStat flag controls whether the failure counts toward the
        // fail_to_cast_great_spell advancement trigger.
        var awarded = new OperatorSideEffect.RequiredEnlightenment(true);
        var notAwarded = new OperatorSideEffect.RequiredEnlightenment(false);
        assertTrue(awarded.getAwardStat());
        assertFalse(notAwarded.getAwardStat());
    }

    @Test
    public void particleSprayBurstFactoryProducesValidFields() {
        // burst() is used on every failed cast. Field values should match the spec:
        // vel = (size, 0, 0); fuzziness = 0; spread = pi (full sphere).
        var burst = ParticleSpray.burst(new Vec3(1, 2, 3), 0.5, 30);
        assertEquals(new Vec3(1, 2, 3), burst.getPos());
        assertEquals(new Vec3(0.5, 0, 0), burst.getVel(), "vel x = size");
        assertEquals(0.0, burst.getFuzziness(), 0.0, "burst fuzziness is 0");
        assertEquals(Math.PI, burst.getSpread(), 0.01, "burst spread is ~pi");
        assertEquals(30, burst.getCount());
    }

    @Test
    public void particleSprayCloudFactoryProducesValidFields() {
        // cloud() is used on many mishap sprays. Vel is near-zero upward, spread=0 (no cone).
        var cloud = ParticleSpray.cloud(new Vec3(0, 10, 0), 1.5, 50);
        assertEquals(new Vec3(0, 10, 0), cloud.getPos());
        assertEquals(0.0, cloud.getVel().x, 0.0, "cloud vel.x = 0");
        assertTrue(cloud.getVel().y > 0, "cloud drifts up");
        assertEquals(1.5, cloud.getFuzziness(), 0.0, "cloud fuzziness = size");
        assertEquals(0.0, cloud.getSpread(), 0.0, "cloud spread is 0");
    }

    @Test
    public void mishapStackSizeHasContextForDoMishap() {
        // Hot-path construction chain: StackSize mishap wrapped in DoMishap. Both must construct
        // without throwing.
        Mishap m = new MishapStackSize();
        var effect = new OperatorSideEffect.DoMishap(m, new Mishap.Context(null, null));
        assertSame(m, effect.getMishap());
    }
}
