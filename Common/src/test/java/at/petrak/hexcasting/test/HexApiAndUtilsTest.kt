package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.addldata.ADMediaHolder
import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.castables.SpecialHandler
import at.petrak.hexcasting.api.utils.SQRT_3
import at.petrak.hexcasting.api.utils.TAU
import at.petrak.hexcasting.api.utils.asTextComponent
import at.petrak.hexcasting.api.utils.extractMedia
import at.petrak.hexcasting.api.utils.findCenter
import at.petrak.hexcasting.api.utils.getSafe
import at.petrak.hexcasting.api.utils.gray
import at.petrak.hexcasting.api.utils.isMediaItem
import at.petrak.hexcasting.api.utils.isOfTag
import at.petrak.hexcasting.api.utils.lightPurple
import at.petrak.hexcasting.api.utils.mediaBarColor
import at.petrak.hexcasting.api.utils.mediaBarWidth
import at.petrak.hexcasting.api.utils.serializeToNBT
import at.petrak.hexcasting.api.utils.vec2FromNBT
import at.petrak.hexcasting.api.utils.vecFromNBT
import at.petrak.hexcasting.api.utils.zipWithDefault
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.common.lib.hex.HexSpecialHandlers
import com.mojang.serialization.Lifecycle
import net.minecraft.ChatFormatting
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Covers pure-function API surface on [HexAPI] and the Kotlin utility files
 * `HexUtils.kt` and `MediaHelper.kt`. Everything here avoids server/world state — the only
 * "stateful" piece is `FakeMediaHolder` below, a minimal [ADMediaHolder] implementation
 * used to drive the media-dispatch layer without needing a real ItemStack.
 *
 * Rationale for covered functions:
 *  - [HexAPI.modLoc] is the universal mod-namespace helper; every registry key, every
 *    datapack JSON lookup, every packet id flows through it. A regression that returned
 *    the wrong namespace would invalidate every resource in the mod.
 *  - [HexAPI.getActionI18nKey] / [HexAPI.getSpecialHandlerI18nKey] build the `.action.<id>`
 *    / `.special.<id>` translation keys the client uses to render spell names. Key-format
 *    drift would leave every spell name as its raw translation key in-game.
 *  - [findCenter] underpins spiral-pattern centering in the GUI and spell previews.
 *  - [getSafe] on enum arrays is the fallback used by `ResolvedPatternType.fromString`
 *    on missing/garbage save data. Case-insensitive and bounds-safe.
 *  - [isOfTag] is how hex recognises per-world patterns at pattern-recognition time;
 *    testing with a fresh registry + fake tag key exercises the miss path.
 *  - [mediaBarColor] / [mediaBarWidth] are the Mth.lerp-based renderers for the HUD bar —
 *    mid-range values must stay monotone, endpoints must match documented colours.
 *  - [extractMedia] with a fake holder confirms simulate/non-simulate branching and
 *    negative-cost ("extract all") semantics without touching an ItemStack.
 *  - Component colour extensions (`lightPurple`, `gray`) must actually apply a style
 *    with the matching [ChatFormatting] — a regression could silently return the input
 *    unchanged.
 *  - `serializeToNBT` on [Vec3] / [Vec2] and the inverse `vecFromNBT` / `vec2FromNBT`
 *    are used in every position-carrying iota; this test guards the NBT shape.
 *  - [zipWithDefault] is the accessor that backs paren-escape tracking in `CastingImage`;
 *    a wrong default-fill index would silently mis-escape entries.
 */
class HexApiAndUtilsTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = TestBootstrap.init()
    }

    // =========================================================================================
    // HexAPI: modLoc + getActionI18nKey + getSpecialHandlerI18nKey + instance()
    // =========================================================================================

    @Test
    fun modLocProducesHexNamespacedLocations() {
        val loc = HexAPI.modLoc("focus")
        assertEquals(HexAPI.MOD_ID, loc.namespace, "namespace is the hex mod id")
        assertEquals("focus", loc.path, "path is the string we passed in")
        assertEquals("hexcasting:focus", loc.toString(),
            "toString should be the canonical hexcasting:<path>")
    }

    @Test
    fun modLocSupportsSlashedPaths() {
        // Block-entities use slashed paths like "impetus/look". modLoc must accept that.
        val loc = HexAPI.modLoc("impetus/look")
        assertEquals("hexcasting", loc.namespace)
        assertEquals("impetus/look", loc.path)
        assertEquals("hexcasting:impetus/look", loc.toString())
    }

    @Test
    fun modLocProducesNonNullNonEmptyResult() {
        // Every registry key in the mod flows through this — a null or empty return would
        // crash the registry layer at <clinit>. Guard the contract explicitly.
        val loc = HexAPI.modLoc("x")
        assertNotNull(loc)
        assertFalse(loc.namespace.isEmpty(), "namespace must never be empty")
        assertFalse(loc.path.isEmpty(), "path must never be empty for a non-empty input")
    }

    @Test
    fun modLocIsUsedForWellKnownUserdataKeys() {
        // HexAPI exposes string constants built from modLoc. Their values are embedded in
        // every ravenmind save and every op-count readout; drift would desync ravenmind
        // across upgrade.
        assertEquals("hexcasting:ravenmind", HexAPI.RAVENMIND_USERDATA)
        assertEquals("hexcasting:op_count", HexAPI.OP_COUNT_USERDATA)
        assertEquals("hexcasting:impulsed", HexAPI.MARKED_MOVED_USERDATA)
    }

    @Test
    fun hexApiInstanceIsBoundAndDefaultImplementable() {
        // HexAPI.instance() uses Guava Suppliers.memoize; should never return null.
        // The real impl may be HexAPIImpl or the anonymous dummy — either is fine as long
        // as the interface's default methods remain callable.
        val api = HexAPI.instance()
        assertNotNull(api, "HexAPI.instance() is always bound")
        // Default methods delegate to String.formatted — exercise via getRawHookI18nKey:
        val rawKey = api.getRawHookI18nKey(HexAPI.modLoc("introspection"))
        assertEquals("hexcasting.rawhook.hexcasting:introspection", rawKey)
    }

    @Test
    fun getActionI18nKeyBuildsHexcastingPrefix() {
        // The key shape hex.lang expects is `hexcasting.action.<namespaced_id>`. A stray
        // dot or missing prefix produces a missing translation in-game.
        val actionLoc = HexAPI.modLoc("get_caster")
        val rk: ResourceKey<ActionRegistryEntry> = ResourceKey.create(HexRegistries.ACTION, actionLoc)
        val key = HexAPI.instance().getActionI18nKey(rk)
        assertEquals("hexcasting.action.hexcasting:get_caster", key)
    }

    @Test
    fun getSpecialHandlerI18nKeyBuildsHexcastingPrefix() {
        // Same contract as action keys, but under .special.
        val specLoc = HexAPI.modLoc("number")
        val rk: ResourceKey<SpecialHandler.Factory<*>> =
            ResourceKey.create(HexRegistries.SPECIAL_HANDLER, specLoc)
        val key = HexAPI.instance().getSpecialHandlerI18nKey(rk)
        assertEquals("hexcasting.special.hexcasting:number", key)
    }

    @Test
    fun getRawHookI18nKeyWorksWithNonHexNamespace() {
        // The raw-hook path accepts any ResourceLocation (introspection / retrospection /
        // consideration ship with modLoc, but nothing stops a different namespace).
        val loc = ResourceLocation.fromNamespaceAndPath("minecraft", "foo")
        assertEquals("hexcasting.rawhook.minecraft:foo",
            HexAPI.instance().getRawHookI18nKey(loc))
    }

    // =========================================================================================
    // HexUtils: findCenter
    // =========================================================================================

    @Test
    fun findCenterOfRectangleIsIntuitiveCentroid() {
        // For a rectangle, the centre-of-bounding-box equals the centroid. A wrong formula
        // would yield (0,0) or a corner instead.
        val points = listOf(
            Vec2(0f, 0f),
            Vec2(4f, 0f),
            Vec2(4f, 2f),
            Vec2(0f, 2f)
        )
        val center = findCenter(points)
        assertEquals(2f, center.x, 0f, "x center of [0,4] is 2")
        assertEquals(1f, center.y, 0f, "y center of [0,2] is 1")
    }

    @Test
    fun findCenterOfSinglePointIsThatPoint() {
        val center = findCenter(listOf(Vec2(3.5f, -7.25f)))
        assertEquals(3.5f, center.x, 0f)
        assertEquals(-7.25f, center.y, 0f)
    }

    @Test
    fun findCenterWithNegativeCoordinatesAveragesBounds() {
        // Includes negative coordinates to verify we don't accidentally abs() anything.
        val points = listOf(Vec2(-4f, -2f), Vec2(2f, 6f))
        val center = findCenter(points)
        assertEquals(-1f, center.x, 0f, "(-4 + 2) / 2 = -1")
        assertEquals(2f, center.y, 0f, "(-2 + 6) / 2 = 2")
    }

    // =========================================================================================
    // HexUtils: getSafe (enum array extensions)
    // =========================================================================================

    @Test
    fun getSafeByStringMatchesCaseInsensitively() {
        // ResolvedPatternType.fromString lowercases both sides; verify the underlying
        // contract on any enum.
        val values = InteractionHand.values()
        assertSame(InteractionHand.MAIN_HAND, values.getSafe("main_hand"))
        assertSame(InteractionHand.MAIN_HAND, values.getSafe("MAIN_HAND"))
        assertSame(InteractionHand.MAIN_HAND, values.getSafe("Main_Hand"))
        assertSame(InteractionHand.OFF_HAND, values.getSafe("off_hand"))
    }

    @Test
    fun getSafeFallsBackToFirstElementForUnknownString() {
        // The documented fallback for unknown enum names is values()[0].
        val values = InteractionHand.values()
        val unknown = values.getSafe("no-such-enum-name")
        assertSame(values[0], unknown, "unknown name -> values()[0]")
    }

    @Test
    fun getSafeByStringUsesExplicitDefaultWhenProvided() {
        val values = InteractionHand.values()
        val out = values.getSafe("nope", InteractionHand.OFF_HAND)
        assertSame(InteractionHand.OFF_HAND, out, "explicit default overrides the values()[0] fallback")
    }

    @Test
    fun getSafeByIntIndexReturnsElementOrDefault() {
        val values = InteractionHand.values()
        assertSame(values[0], values.getSafe(0))
        assertSame(values[1], values.getSafe(1))
        assertSame(values[0], values.getSafe(99),
            "out-of-range index falls back to values()[0]")
        assertSame(InteractionHand.OFF_HAND, values.getSafe(-1, InteractionHand.OFF_HAND),
            "negative index with explicit default returns the default")
    }

    @Test
    fun getSafeByByteDelegatesToIntPath() {
        val values = InteractionHand.values()
        assertSame(values[0], values.getSafe(0.toByte()))
        assertSame(values[1], values.getSafe(1.toByte()))
        assertSame(values[0], values.getSafe(127.toByte()),
            "byte 127 is a valid int 127, which is out of range -> fallback")
    }

    // =========================================================================================
    // HexUtils: isOfTag + ResourceLocation overload
    // =========================================================================================

    @Test
    fun isOfTagReturnsFalseForUnregisteredKey() {
        // A ResourceKey that points to a non-existent entry in the registry -> registry.getHolder
        // returns empty -> isOfTag returns false without throwing.
        val reg: Registry<String> = MappedRegistry(
            ResourceKey.createRegistryKey(HexAPI.modLoc("test_registry")),
            Lifecycle.stable()
        )
        val unknownKey = ResourceKey.create(reg.key(), HexAPI.modLoc("not_present"))
        val fakeTag = TagKey.create(reg.key(), HexAPI.modLoc("any_tag"))
        assertFalse(isOfTag(reg, unknownKey, fakeTag),
            "missing registry entry -> false (no throw)")
    }

    @Test
    fun isOfTagResourceLocationOverloadReturnsFalseForMissingEntry() {
        // The ResourceLocation overload wraps the ResourceKey path; verify it follows
        // the same "missing entry -> false" contract.
        val reg: Registry<String> = MappedRegistry(
            ResourceKey.createRegistryKey(HexAPI.modLoc("test_registry_loc")),
            Lifecycle.stable()
        )
        val fakeTag = TagKey.create(reg.key(), HexAPI.modLoc("any_tag"))
        assertFalse(isOfTag(reg, HexAPI.modLoc("missing"), fakeTag),
            "ResourceLocation overload also returns false for missing entries")
    }

    // =========================================================================================
    // HexUtils: Vec3 / Vec2 NBT round-trips
    // =========================================================================================

    @Test
    fun vec3RoundTripsThroughCompoundTag() {
        val original = Vec3(1.5, -2.25, 3.75)
        val tag: CompoundTag = original.serializeToNBT()
        val back = vecFromNBT(tag)
        assertEquals(1.5, back.x, 0.0)
        assertEquals(-2.25, back.y, 0.0)
        assertEquals(3.75, back.z, 0.0)
    }

    @Test
    fun vecFromNbtOnIncompleteTagFallsBackToZero() {
        // Missing any of x/y/z -> Vec3.ZERO. Guards loaders against corrupted iotas.
        val partial = CompoundTag()
        partial.putDouble("x", 42.0)
        // omit y and z
        assertEquals(Vec3.ZERO, vecFromNBT(partial))
    }

    @Test
    fun vec3FromLongArrayRoundTripPreservesBits() {
        // Alternative Vec3 encoding: 3 raw-bit doubles. Off-by-one size -> ZERO.
        val bits = longArrayOf(
            (1.125).toRawBits(),
            (2.25).toRawBits(),
            (3.5).toRawBits()
        )
        val back = vecFromNBT(bits)
        assertEquals(1.125, back.x, 0.0)
        assertEquals(2.25, back.y, 0.0)
        assertEquals(3.5, back.z, 0.0)

        // Wrong array size -> zero fallback.
        assertEquals(Vec3.ZERO, vecFromNBT(longArrayOf(1L, 2L)))
    }

    @Test
    fun vec2RoundTripsThroughLongArrayTag() {
        val original = Vec2(3.25f, -1.5f)
        val tag = original.serializeToNBT()
        // LongArrayTag.asLongArray exposes the backing long[].
        val back = vec2FromNBT(tag.asLongArray)
        assertEquals(3.25f, back.x, 0f)
        assertEquals(-1.5f, back.y, 0f)
    }

    @Test
    fun vec2FromMalformedArrayFallsBackToZero() {
        // Length != 2 -> Vec2.ZERO. Any other length would be outside the encoding contract.
        assertEquals(Vec2.ZERO, vec2FromNBT(longArrayOf(1L)))
        assertEquals(Vec2.ZERO, vec2FromNBT(longArrayOf(1L, 2L, 3L)))
    }

    // =========================================================================================
    // HexUtils: zipWithDefault
    // =========================================================================================

    @Test
    fun zipWithDefaultPairsElementsUntilArrayExhausts() {
        // When the ByteArray is shorter than the list, remaining entries use the default
        // function. CastingImage uses this for paren-escape tracking.
        val list = listOf("a", "b", "c", "d")
        val bytes = byteArrayOf(10, 20)  // shorter than list -> last two use default
        val zipped = list.zipWithDefault(bytes) { idx -> (100 + idx).toByte() }
        assertEquals(4, zipped.size)
        assertEquals("a" to 10.toByte(), zipped[0])
        assertEquals("b" to 20.toByte(), zipped[1])
        assertEquals("c" to 102.toByte(), zipped[2], "idx 2 with default(2) = 102")
        assertEquals("d" to 103.toByte(), zipped[3], "idx 3 with default(3) = 103")
    }

    @Test
    fun zipWithDefaultWithFullyCoveringArray() {
        // When the array is at least as long as the list, every pair uses the array value.
        val list = listOf("x", "y")
        val bytes = byteArrayOf(7, 8, 9)  // array has an extra trailing element
        val zipped = list.zipWithDefault(bytes) { error("default should not be called") }
        assertEquals(listOf("x" to 7.toByte(), "y" to 8.toByte()), zipped)
    }

    // =========================================================================================
    // HexUtils: Component colour extensions
    // =========================================================================================

    @Test
    fun lightPurpleAppliesLightPurpleColour() {
        // `"text".lightPurple` returns a MutableComponent with LIGHT_PURPLE style applied.
        val comp = "hello".lightPurple
        assertEquals("hello", comp.string, "literal text content preserved")
        val color = comp.style.color
        assertNotNull(color, "LIGHT_PURPLE must attach a colour to the style")
        assertEquals(ChatFormatting.LIGHT_PURPLE.color!!.toInt(), color!!.value,
            "style carries the light-purple rgb value")
    }

    @Test
    fun grayAppliesGrayColour() {
        val comp = "dim".gray
        val color = comp.style.color
        assertNotNull(color, "GRAY must attach a colour to the style")
        assertEquals(ChatFormatting.GRAY.color!!.toInt(), color!!.value,
            "gray extension applies ChatFormatting.GRAY")
    }

    @Test
    fun asTextComponentReturnsLiteralMutableComponent() {
        val comp = "foo".asTextComponent
        assertEquals("foo", comp.string, "literal wraps the exact string")
    }

    // =========================================================================================
    // HexUtils: constants + TAU
    // =========================================================================================

    @Test
    fun tauIsTwoPi() {
        assertEquals(Math.PI * 2.0, TAU, 0.0,
            "TAU must be exactly 2π — any drift would mis-rotate every spell circle")
    }

    @Test
    fun sqrt3IsKnownValue() {
        assertEquals(1.7320508f, SQRT_3, 0f,
            "SQRT_3 must match the hard-coded hex grid constant")
    }

    // =========================================================================================
    // MediaHelper: mediaBarColor / mediaBarWidth
    // =========================================================================================

    @Test
    fun mediaBarWidthSpansZeroToThirteen() {
        // Vanilla item-durability bar width is 13 pixels. Hex's media bar uses the same span.
        assertEquals(0, mediaBarWidth(0L, 100L),
            "empty holder -> 0 width")
        assertEquals(13, mediaBarWidth(100L, 100L),
            "full holder -> 13 width (full vanilla-style bar)")
        assertEquals(7, mediaBarWidth(50L, 100L),
            "half-full holder rounds half-up from 6.5 to 7")
    }

    @Test
    fun mediaBarWidthHandlesZeroMaxSafely() {
        // Division guard: when maxMedia == 0, the amt is forced to 0 rather than NaN.
        // Otherwise the rendered bar would disappear entirely or crash.
        assertEquals(0, mediaBarWidth(0L, 0L),
            "zero capacity -> 0 width (not NaN -> 0)")
        assertEquals(0, mediaBarWidth(50L, 0L),
            "zero capacity with non-zero fill still renders 0")
    }

    @Test
    fun mediaBarColorHasStableEndpoints() {
        // Empty bar: lerp amt = 0 -> rgb(84, 57, 138) = 0x54398A. Full bar: amt = 1 ->
        // rgb(254, 203, 230) = 0xFECBE6. Mth.color packs without alpha in 1.21 (consumers
        // add alpha downstream), so the raw value is 0x00RRGGBB.
        val empty = mediaBarColor(0L, 100L)
        val full = mediaBarColor(100L, 100L)
        assertEquals(0x54398A, empty,
            "empty endpoint matches documented purple (84, 57, 138)")
        assertEquals(0xFECBE6, full,
            "full endpoint matches documented pink (254, 203, 230)")
    }

    @Test
    fun mediaBarColorHandlesZeroMaxWithoutNaN() {
        // With max = 0, the ratio is forced to 0 and the colour becomes the empty-bar colour.
        val color = mediaBarColor(0L, 0L)
        assertEquals(0x54398A, color,
            "zero capacity -> empty-bar colour (no NaN pollution)")
    }

    // =========================================================================================
    // MediaHelper: isMediaItem + extractMedia (via a fake ADMediaHolder)
    // =========================================================================================

    @Test
    fun extractMediaSimulateDoesNotMutateHolder() {
        // simulate=true must report the withdrawable amount without mutating. Non-simulate
        // mutates. Both paths route through ADMediaHolder.withdrawMedia.
        val holder = FakeMediaHolder(initialMedia = 100L, maxMedia = 500L,
            canProvideFlag = true, canConstructBatteryFlag = true)
        val reported = extractMedia(holder, cost = 30L, simulate = true)
        assertEquals(30L, reported, "simulated extract reports the cost")
        assertEquals(100L, holder.mediaAmount, "simulate did not mutate media")

        val actual = extractMedia(holder, cost = 30L, simulate = false)
        assertEquals(30L, actual, "real extract reports the cost")
        assertEquals(70L, holder.mediaAmount, "real extract mutated media by 30")
    }

    @Test
    fun extractMediaNegativeCostExtractsAll() {
        val holder = FakeMediaHolder(initialMedia = 42L, maxMedia = 1000L,
            canProvideFlag = true, canConstructBatteryFlag = true)
        val extracted = extractMedia(holder, cost = -1L, simulate = false)
        assertEquals(42L, extracted, "negative cost = extract everything")
        assertEquals(0L, holder.mediaAmount, "holder drained to zero")
    }

    @Test
    fun extractMediaFromItemStackWithoutHolderReturnsZero() {
        // Under the stub IXplatAbstractions, findMediaHolder(stack) returns null, so the
        // ItemStack overload short-circuits to 0 without going through the holder path.
        val out = extractMedia(ItemStack.EMPTY, 100L, false, false)
        assertEquals(0L, out, "stub stack with no holder -> 0 extracted")
    }

    @Test
    fun isMediaItemReturnsFalseForStubStack() {
        // Same short-circuit: stub returns null -> isMediaItem is false.
        assertFalse(isMediaItem(ItemStack.EMPTY),
            "stub stack with no holder is not a media item")
    }

    @Test
    fun extractMediaDrainForBatteriesRespectsHolderCapability() {
        // When drainForBatteries=true, a holder that can't construct a battery returns 0
        // regardless of stored media.
        val nonBattery = FakeMediaHolder(initialMedia = 100L, maxMedia = 200L,
            canProvideFlag = true, canConstructBatteryFlag = false)
        assertEquals(0L, extractMedia(nonBattery, cost = 50L, drainForBatteries = true),
            "non-battery holder refuses battery drain")

        val battery = FakeMediaHolder(initialMedia = 100L, maxMedia = 200L,
            canProvideFlag = true, canConstructBatteryFlag = true)
        assertEquals(50L, extractMedia(battery, cost = 50L, drainForBatteries = true,
            simulate = true),
            "battery-capable holder proceeds with the drain")
    }

    // =========================================================================================
    // Cross-integration: getSpecialHandlerI18nKey against the live HexSpecialHandlers instance
    // =========================================================================================

    @Test
    fun specialHandlerI18nKeyFormatMatchesExpectedDataPackKey() {
        // Cross-check the HexSpecialHandlers entries against the documented lang-key format.
        // A data-pack author inspecting hexcasting.special.hexcasting:number expects this exact
        // string to index into the localisation file.
        assertNotNull(HexSpecialHandlers.NUMBER, "NUMBER handler present")
        assertNotNull(HexSpecialHandlers.MASK, "MASK handler present")
        // Direct key-shape check (doesn't require a registry lookup — we know the loc).
        val numberKey: ResourceKey<SpecialHandler.Factory<*>> =
            ResourceKey.create(HexRegistries.SPECIAL_HANDLER, HexAPI.modLoc("number"))
        assertEquals("hexcasting.special.hexcasting:number",
            HexAPI.instance().getSpecialHandlerI18nKey(numberKey))
    }

    // =========================================================================================
    // Test fixture: minimal ADMediaHolder that tracks reads/writes on a mutable field.
    // =========================================================================================

    private class FakeMediaHolder(
        initialMedia: Long,
        private val maxMedia: Long,
        private val canProvideFlag: Boolean,
        private val canConstructBatteryFlag: Boolean
    ) : ADMediaHolder {
        // Kotlin would synthesize getMedia()/setMedia() from a `var media`, clashing with the
        // Java interface's getMedia()/setMedia methods. Use a plain mutable field and expose via
        // the explicit overrides only.
        @JvmField var mediaAmount: Long = initialMedia

        override fun getMedia(): Long = mediaAmount
        override fun getMaxMedia(): Long = maxMedia
        override fun setMedia(media: Long) { this.mediaAmount = media }
        override fun canRecharge(): Boolean = true
        override fun canProvide(): Boolean = canProvideFlag
        override fun getConsumptionPriority(): Int = 0
        override fun canConstructBattery(): Boolean = canConstructBatteryFlag
    }
}
