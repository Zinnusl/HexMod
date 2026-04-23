package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.castables.OperationAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.math.HexAngle
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.casting.PatternRegistryManifest
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Headless tests for [CastingVM], [PatternRegistryManifest], and the pure-data slice of
 * [OperationAction]. The surface we can exercise without a live `ServerPlayer` / `ServerLevel` is
 * narrow; anything that runs a spell pipeline end-to-end (e.g.
 * `StaffCastEnv.handleNewPatternOnServer`, `CircleCastEnv`, `PackagedItemCastEnv`) is skipped.
 *
 * Written in Kotlin because [CastingImage] is a Kotlin data class with a private primary
 * constructor — [CastingImage.copy] is the only public way to set `userData` without going through
 * `loadFromNbt` (which needs a `ServerLevel`).
 *
 * Covered:
 *  - [CastingVM.empty] — the sole pure `@JvmStatic` helper on the VM companion. Builds a VM around
 *    a new empty image and the given env, preserving the env ref.
 *  - Primary [CastingVM] ctor: exercises the `init { env.triggerCreateEvent(...) }` block against
 *    a [StubCastingEnv]; verifies both stored refs survive.
 *  - [CastingVM.generateDescs] — pure function reading `image`. Empty image -> (empty list, null
 *    ravenmind). Image with a ravenmind userdata key -> (empty list, the ravenmind compound).
 *  - [CastingVM.performSideEffects] with an empty list — no-op (regression guard against a loop
 *    bug that required non-empty input).
 *  - [PatternRegistryManifest.processRegistry] on a null overworld — documented client-side path.
 *    Idempotent — second call just rebuilds the lookup.
 *  - [PatternRegistryManifest.matchPatternToSpecialHandler] — null when the special-handler
 *    registry is empty (the test stub registry never gets populated).
 *  - [OperationAction] — data-class constructor + getter, equals/hashCode, component1.
 *
 * Skipped:
 *  - `StaffCastEnv.handleNewPatternOnServer`, `PackagedItemCastEnv`, `CircleCastEnv` — all need
 *    a real `ServerPlayer` or `ServerLevel`. No pure statics to exercise.
 *  - `PlayerBasedSpiralPatternCastEnv.getSpiralPatternDuration()` is abstract; concrete subclasses
 *    all need a `ServerPlayer`.
 *  - [PatternRegistryManifest.getCanonicalStrokesPerWorld] takes a `ServerLevel`.
 *  - [PatternRegistryManifest.matchPattern] reaches through `environment.getWorld().getServer()`,
 *    which [StubCastingEnv] NPEs on.
 *  - [OperationAction.operate] needs a populated arithmetic registry (our test stub leaves it
 *    empty) and would mutate the cached `HexArithmetics.ENGINE` singleton across tests.
 */
class CastingVMAndEnvTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = TestBootstrap.init()
    }

    private val env: CastingEnvironment = StubCastingEnv()

    // === CastingVM.empty and ctor =============================================================

    @Test
    fun emptyReturnsVMWithEmptyImageAndGivenEnv() {
        // CastingVM.empty is the single pure entry point on the VM companion. Its whole contract is:
        // new empty image, keep the env reference. A regression here (e.g. null env, stale image)
        // would silently break every code path that creates a fresh VM mid-cast.
        val vm = CastingVM.empty(env)
        assertNotNull(vm, "empty() returns a non-null VM")
        assertSame(env, vm.env, "VM holds the exact env passed in")
        val image = vm.image
        assertNotNull(image, "empty VM has a non-null image")
        assertTrue(image.stack.isEmpty(), "empty VM starts with empty stack")
        assertEquals(0, image.parenCount, "empty VM has no parens open")
        assertFalse(image.escapeNext, "empty VM has escape flag cleared")
        assertEquals(0L, image.opsConsumed, "empty VM has zero ops consumed")
        assertTrue(image.userData.isEmpty, "empty VM has empty userdata")
    }

    @Test
    fun emptyTwiceYieldsDistinctInstances() {
        // Two calls to empty() with the same env must return different VM instances carrying
        // different CastingImage refs — otherwise callers that mutate one would accidentally
        // share state with anyone else who got the same empty VM.
        val vm1 = CastingVM.empty(env)
        val vm2 = CastingVM.empty(env)
        assertNotSame(vm1, vm2, "empty() returns a fresh VM each call")
        assertNotSame(vm1.image, vm2.image, "and a fresh CastingImage each call")
        assertSame(env, vm1.env)
        assertSame(env, vm2.env)
    }

    @Test
    fun constructorPreservesSuppliedImageAndEnv() {
        // Primary ctor path: CastingVM(image, env). Verifies the init {} block's
        // triggerCreateEvent call doesn't crash against a StubCastingEnv (no listeners registered
        // in test context, so the loop is a no-op).
        val seed = CastingImage().withUsedOps(42L)
        val vm = CastingVM(seed, env)
        assertSame(seed, vm.image, "VM holds exact image ref passed in")
        assertEquals(42L, vm.image.opsConsumed, "image's opsConsumed survives ctor")
        assertSame(env, vm.env, "VM holds env ref passed in")
    }

    @Test
    fun imageFieldIsMutableOnTheVM() {
        // CastingVM.image is `var image: CastingImage` — the main cast loop reassigns it after
        // every iota.
        val vm = CastingVM.empty(env)
        val newImage = CastingImage().withUsedOps(7L)
        vm.image = newImage
        assertSame(newImage, vm.image, "var image setter wires through")
        assertEquals(7L, vm.image.opsConsumed)
    }

    // === CastingVM.generateDescs pure behaviour ===============================================

    @Test
    fun generateDescsOnEmptyImageYieldsEmptyStackAndNullRavenmind() {
        // generateDescs inspects the current image: empty stack -> empty List<CompoundTag>,
        // missing ravenmind userdata key -> null tag. Both are what the client sees when nothing
        // has been cast yet.
        val vm = CastingVM.empty(env)
        val (stackDescs, ravenmind) = vm.generateDescs()
        assertTrue(stackDescs.isEmpty(), "empty image yields empty stackDescs")
        assertNull(ravenmind, "no ravenmind key -> null ravenmind tag")
    }

    @Test
    fun generateDescsSurfacesRavenmindWhenPresent() {
        // Populate userdata with the ravenmind compound — generateDescs should return it verbatim
        // as the second element of the pair. Exercises the `if userData.contains(...)` branch.
        val ravenmind = CompoundTag()
        ravenmind.putString("marker", "hello")
        val userData = CompoundTag()
        userData.put(HexAPI.RAVENMIND_USERDATA, ravenmind)

        val image = CastingImage().copy(userData = userData)
        val vm = CastingVM(image, env)
        val (stackDescs, surfacedRavenmind) = vm.generateDescs()
        assertTrue(stackDescs.isEmpty(), "empty stack still empty descs")
        assertNotNull(surfacedRavenmind, "ravenmind key present -> tag returned")
        assertEquals("hello", surfacedRavenmind!!.getString("marker"),
            "ravenmind compound content preserved")
    }

    // === performSideEffects empty-list safety =================================================

    @Test
    fun performSideEffectsOnEmptyListIsNoOp() {
        // Fresh VM, empty side-effect list — the loop body never runs, nothing should throw.
        // Cheap regression guard for the core mid-cast control-flow helper.
        val vm = CastingVM.empty(env)
        vm.performSideEffects(emptyList())
        // State unchanged
        assertTrue(vm.image.stack.isEmpty())
        assertEquals(0L, vm.image.opsConsumed)
    }

    // === PatternRegistryManifest ==============================================================

    @Test
    fun processRegistryNullOverworldDoesNotThrow() {
        // Called on the client side at connection time with a null overworld arg. On the server
        // the arg is only used for a log-message branch. TestBootstrap populates the action
        // registry, so the loop iterates real entries.
        assertDoesNotThrow(
            { PatternRegistryManifest.processRegistry(null) },
            "processRegistry(null) is the documented client path and must not throw"
        )
        // Idempotent semantics — re-processing may log 'overriding' warnings for duplicate
        // signatures but should not throw.
        assertDoesNotThrow(
            { PatternRegistryManifest.processRegistry(null) },
            "processRegistry is idempotent — second call rebuilds the lookup without throwing"
        )
    }

    @Test
    fun matchPatternToSpecialHandlerReturnsNullWhenRegistryEmpty() {
        // StubXplatAbstractions's specialHandlerRegistry is constructed empty and never populated
        // (hex's real handlers live in HexSpecialHandlers, which tests don't register). The loop
        // short-circuits to null.
        assertEquals(
            0, IXplatAbstractions.INSTANCE.getSpecialHandlerRegistry().size(),
            "sanity: test stub leaves the special-handler registry empty"
        )
        val pat = HexPattern.fromAngles("aqaaw", HexDir.EAST) // number-literal shape, but no handler registered
        val result = PatternRegistryManifest.matchPatternToSpecialHandler(pat, env)
        assertNull(result, "empty handler registry -> no match")
    }

    @Test
    fun matchPatternToSpecialHandlerHandlesDifferentPatternsUniformly() {
        // Same null-result contract across unrelated pattern shapes. Protects against a regression
        // where the iteration logic short-circuits truthily on an odd shape.
        val patterns = listOf(
            HexPattern.fromAngles("qaq", HexDir.NORTH_EAST),   // GET_CASTER
            HexPattern.fromAngles("aa", HexDir.EAST),          // ENTITY_POS
            HexPattern.fromAngles("wdedw", HexDir.NORTH_EAST), // DIV
            HexPattern.fromAngles("", HexDir.EAST),            // empty pattern
        )
        for (pat in patterns) {
            assertNull(
                PatternRegistryManifest.matchPatternToSpecialHandler(pat, env),
                "pattern ${pat.anglesSignature()} -> no special handler"
            )
        }
    }

    // === OperationAction pure data-class surface ==============================================

    @Test
    fun operationActionConstructorPreservesPattern() {
        // OperationAction is a Kotlin data class wrapping a HexPattern. Constructor + getter is
        // the entire pure surface; `operate()` needs a populated arithmetic engine which the stub
        // xplat doesn't provide.
        val pat = HexPattern.fromAngles("waaw", HexDir.NORTH_EAST) // ADD
        val action = OperationAction(pat)
        assertSame(pat, action.pattern, "pattern ref preserved through ctor")
    }

    @Test
    fun operationActionEqualsAndHashCodeAreStructural() {
        // Kotlin data-class contract: equal pattern -> equal action, same hashCode. Used by the
        // action registry keying / lookup — drift here would cause spurious "duplicate action"
        // warnings.
        val pat1 = HexPattern(HexDir.EAST, arrayListOf(HexAngle.FORWARD))
        val pat2 = HexPattern(HexDir.EAST, arrayListOf(HexAngle.FORWARD))
        val a1 = OperationAction(pat1)
        val a2 = OperationAction(pat2)
        assertEquals(a1, a2, "data-class equality delegates to HexPattern.equals")
        assertEquals(a1.hashCode(), a2.hashCode(), "equal actions share hashCode")

        val patDifferent = HexPattern(HexDir.EAST, arrayListOf(HexAngle.LEFT))
        val a3 = OperationAction(patDifferent)
        assertNotEquals(a1, a3, "different pattern -> different action")
    }

    @Test
    fun operationActionComponent1ReturnsPattern() {
        // Kotlin data-class destructuring via component1() is part of the generated contract —
        // addons may destructure to recover the pattern for their own operator overloads.
        val pat = HexPattern.fromAngles("wdedw", HexDir.NORTH_EAST)
        val action = OperationAction(pat)
        assertSame(pat, action.component1(), "component1() returns the pattern field")
    }

    @Test
    fun operationActionCopyProducesStructurallyEqualClone() {
        // data class copy() without args yields an equal instance. Exercises the synthetic copy
        // method for the only public field.
        val pat = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST)
        val a1 = OperationAction(pat)
        val a2 = a1.copy()
        assertNotSame(a1, a2, "copy() returns a new instance")
        assertEquals(a1, a2, "copy() preserves all fields")
        assertSame(a1.pattern, a2.pattern, "copy without args reuses the pattern ref")
    }
}
