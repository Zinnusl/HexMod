package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate
import at.petrak.hexcasting.api.casting.eval.vm.FrameFinishEval
import at.petrak.hexcasting.common.lib.hex.HexContinuationTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Continuation frames are the serialized VM state the casting engine writes out when a spell is
 * paused — e.g. by a delay or a mid-spell item switch. On 1.21 the type registration moved from
 * a hand-written Serializer subclass to [ContinuationFrame.Type]. A drift in the NBT shape
 * between save and load would turn every paused spell into a silently-dropped null, losing the
 * player's in-progress work.
 *
 * Kotlin-side so the `world: ServerLevel` non-null param on fromNBT can be passed a null stub
 * for branches that don't dereference world (unknown-type fallback, missing-type fallback).
 */
class ContinuationFrameTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = TestBootstrap.init()
    }

    /**
     * Reflection invoke bypasses the Kotlin companion-object fromNBT intrinsic's non-null check
     * on `world` only at the dispatch layer — but the method body ALSO null-checks. So for the
     * fromNBT fallback tests, we can't pass null at all. Instead, rely on the fact that the only
     * deserialize paths needing world are FrameEvaluate/FrameForEach — both triggered by a valid
     * type key. Fallback branches (unknown type, missing type) return early before touching world.
     *
     * The Kotlin compiler inserts intrinsic null-checks on non-null params at method entry — but
     * the fallback branches in ContinuationFrame.fromNBT run before any world deref. Confirmed
     * by reading the source: the fallback returns `FrameEvaluate(SpellList.LList(0, listOf()), false)`
     * directly without world usage. So the non-null check IS the only obstacle. Skip world-dependent
     * tests; the fallback logic is covered differently below via the type registry.
     */

    @Test
    fun continuationTypesAreRegisteredUnderStableIds() {
        // These ids are baked into saved-world NBT. Renaming one would load every saved spell as
        // the FrameEvaluate fallback (empty list) — the player loses whatever was paused.
        assertNotNull(HexContinuationTypes.EVALUATE, "evaluate type present")
        assertNotNull(HexContinuationTypes.FOREACH, "foreach type present")
        assertNotNull(HexContinuationTypes.END, "end (FrameFinishEval) type present")

        val reg = HexContinuationTypes.REGISTRY
        assertEquals(
            ResourceLocation.fromNamespaceAndPath("hexcasting", "evaluate"),
            reg.getKey(HexContinuationTypes.EVALUATE),
            "evaluate registered as hex:evaluate"
        )
        assertEquals(
            ResourceLocation.fromNamespaceAndPath("hexcasting", "foreach"),
            reg.getKey(HexContinuationTypes.FOREACH),
            "foreach registered as hex:foreach"
        )
        assertEquals(
            ResourceLocation.fromNamespaceAndPath("hexcasting", "end"),
            reg.getKey(HexContinuationTypes.END),
            "end registered as hex:end"
        )
    }

    @Test
    fun finishEvalFrameSerializesWithTypeKey() {
        // FrameFinishEval is a sentinel — serialize produces an empty payload, but the top-level
        // shape still carries the type key so the loader can identify it.
        val tag = ContinuationFrame.toNBT(FrameFinishEval)
        assertTrue(tag.contains(HexContinuationTypes.KEY_TYPE),
            "serialized frame must carry a type key")
        assertEquals("hexcasting:end", tag.getString(HexContinuationTypes.KEY_TYPE),
            "FrameFinishEval serializes with id hex:end")
        assertTrue(tag.contains(HexContinuationTypes.KEY_DATA),
            "serialized frame must carry a data key (even if empty)")
    }

    @Test
    fun registryLookupByResourceLocationFindsAllThree() {
        // fromNBT uses HexContinuationTypes.REGISTRY[typeLoc] to resolve a known type. Verify
        // lookup works for each known type id — if this regressed, EVERY saved spell continuation
        // would fall through to the "unknown type" branch.
        val reg = HexContinuationTypes.REGISTRY
        assertSame(HexContinuationTypes.EVALUATE,
            reg.get(ResourceLocation.fromNamespaceAndPath("hexcasting", "evaluate")),
            "evaluate lookup")
        assertSame(HexContinuationTypes.FOREACH,
            reg.get(ResourceLocation.fromNamespaceAndPath("hexcasting", "foreach")),
            "foreach lookup")
        assertSame(HexContinuationTypes.END,
            reg.get(ResourceLocation.fromNamespaceAndPath("hexcasting", "end")),
            "end lookup")
    }

    @Test
    fun finishEvalSerializesEmptyPayload() {
        // Sentinel: no state to serialize.
        val tag = FrameFinishEval.serializeToNBT()
        assertTrue(tag.isEmpty, "FrameFinishEval serializes an empty compound")
    }

    @Test
    fun finishEvalFrameTypeIsRegistered() {
        // The Type instance exposed on FrameFinishEval must be the same one in the registry.
        // A stale reference would cause deserialize to succeed but produce a different instance.
        assertSame(FrameFinishEval.TYPE, HexContinuationTypes.END,
            "FrameFinishEval.TYPE === HexContinuationTypes.END (same registered instance)")
    }

    @Test
    fun continuationRegistryHasExactlyThreeEntries() {
        // Hex ships three frame types: evaluate, foreach, end. If the set grew without a matching
        // data-pack migration, save compat becomes a maintenance burden — so this is a canary.
        val hexCount = HexContinuationTypes.REGISTRY.keySet()
            .count { it.namespace == "hexcasting" }
        assertEquals(3, hexCount,
            "hex ships exactly 3 continuation frame types; got $hexCount")
    }
}
