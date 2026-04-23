package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.circles.BlockEntityAbstractImpetus;
import at.petrak.hexcasting.api.casting.circles.CircleExecutionState;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.blocks.akashic.BlockEntityAkashicBookshelf;
import at.petrak.hexcasting.common.blocks.circles.BlockEntitySlate;
import at.petrak.hexcasting.common.blocks.circles.impetuses.BlockEntityLookingImpetus;
import at.petrak.hexcasting.common.blocks.circles.impetuses.BlockEntityRedstoneImpetus;
import at.petrak.hexcasting.common.lib.HexBlockEntities;
import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-NBT shape tests for block entities that otherwise need a {@code ServerLevel} to run.
 * <p>
 * All three impetus BEs plus the slate and akashic bookshelf override
 * {@link at.petrak.hexcasting.api.block.HexBlockEntity#saveModData} /
 * {@link at.petrak.hexcasting.api.block.HexBlockEntity#loadModData}. The ticking paths (circle
 * execution, setNewMapping broadcast, etc.) can't run headless because they call
 * {@code this.level.setBlock} or {@code registryAccess()}, but the save/load sides are pure NBT
 * transforms — this is the one place where save-compat regressions bite the hardest, because a
 * broken round-trip silently drops stored data on chunk reload.
 * <p>
 * {@link CircleExecutionState#save(net.minecraft.core.HolderLookup.Provider)} is the other
 * pure-enough target: the {@code load} side hydrates a {@code CastingImage} against a ServerLevel,
 * but the save side just emits a CompoundTag whose key-set is the save-compat contract.
 */
public final class BlockEntityDataTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
        // Force HexBlockEntities to initialize — several BEs reference its fields in their
        // constructors. Test order isn't guaranteed, so ensure the <clinit> has run before we
        // reach any BE constructor. Reading a random BE type field is enough.
        assertNotNull(HexBlockEntities.SLATE_TILE, "SLATE_TILE static init succeeded");
    }

    // ---------- helpers ----------

    /**
     * The save/load methods on HexBlockEntity are protected. Drive them via reflection so the
     * test can stay in the {@code at.petrak.hexcasting.test} package alongside every other test.
     */
    private static void saveModData(Object be, CompoundTag tag) {
        try {
            Method m = findMethod(be.getClass(), "saveModData", CompoundTag.class);
            m.setAccessible(true);
            m.invoke(be, tag);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadModData(Object be, CompoundTag tag) {
        try {
            Method m = findMethod(be.getClass(), "loadModData", CompoundTag.class);
            m.setAccessible(true);
            m.invoke(be, tag);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) throws NoSuchMethodException {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getField(Object target, String name) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- BlockEntitySlate ----------

    @Test
    public void slateBlockEntitySavesPattern() {
        // Slate BE stores its pattern as a CompoundTag under TAG_PATTERN. The game relies on this
        // shape when chunks unload/reload — drift would lose every ritual slate's stored pattern.
        var be = new BlockEntitySlate(BlockPos.ZERO, HexBlocks.SLATE.defaultBlockState());
        var pattern = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        be.pattern = pattern;

        var tag = new CompoundTag();
        saveModData(be, tag);

        assertTrue(tag.contains(BlockEntitySlate.TAG_PATTERN, Tag.TAG_COMPOUND),
            "saved pattern under TAG_PATTERN as a CompoundTag");
        assertTrue(HexPattern.isPattern(tag.getCompound(BlockEntitySlate.TAG_PATTERN)),
            "saved tag is a valid HexPattern NBT");
    }

    @Test
    public void slateBlockEntityRoundTripsPattern() {
        var src = new BlockEntitySlate(BlockPos.ZERO, HexBlocks.SLATE.defaultBlockState());
        var pattern = HexPattern.fromAngles("aawdd", HexDir.EAST);
        src.pattern = pattern;

        var tag = new CompoundTag();
        saveModData(src, tag);

        var dst = new BlockEntitySlate(BlockPos.ZERO, HexBlocks.SLATE.defaultBlockState());
        loadModData(dst, tag);

        assertNotNull(dst.pattern, "pattern survived round-trip");
        assertEquals(pattern.anglesSignature(), dst.pattern.anglesSignature(),
            "angles signature preserved");
        assertEquals(pattern.getStartDir(), dst.pattern.getStartDir(),
            "start direction preserved");
    }

    @Test
    public void slateBlockEntityWritesEmptyCompoundForNullPattern() {
        // When no pattern is set the slate still writes an (empty) compound under TAG_PATTERN —
        // the load side uses isPattern() to distinguish, so the write-side must not omit the key
        // entirely. A regression where null → no key would flip BlockEntitySlate.loadModData onto
        // the "missing key" branch and then onto the "null pattern" branch — safe here but
        // brittle, so pin the shape.
        var be = new BlockEntitySlate(BlockPos.ZERO, HexBlocks.SLATE.defaultBlockState());
        be.pattern = null;

        var tag = new CompoundTag();
        saveModData(be, tag);

        assertTrue(tag.contains(BlockEntitySlate.TAG_PATTERN),
            "empty slate still writes TAG_PATTERN key");
        assertTrue(tag.getCompound(BlockEntitySlate.TAG_PATTERN).isEmpty(),
            "empty slate writes an empty compound");
    }

    @Test
    public void slateBlockEntityLoadsNullWhenTagIsInvalid() {
        // Defensive test: an empty CompoundTag (present but not a valid HexPattern) should result
        // in null pattern, not a crash. This is the "chunk saved before the slate was given a
        // pattern" case.
        var dst = new BlockEntitySlate(BlockPos.ZERO, HexBlocks.SLATE.defaultBlockState());
        dst.pattern = HexPattern.fromAngles("qqq", HexDir.NORTH_EAST); // pre-seed

        var tag = new CompoundTag();
        tag.put(BlockEntitySlate.TAG_PATTERN, new CompoundTag());
        loadModData(dst, tag);

        assertNull(dst.pattern, "invalid pattern NBT loads to null, not a garbage pattern");
    }

    // ---------- BlockEntityAkashicBookshelf ----------

    @Test
    public void bookshelfSavesDummyTagWhenEmpty() {
        // An empty bookshelf doesn't write pattern/iota keys; it writes TAG_DUMMY=false. This is
        // the shape the block uses to distinguish "haven't placed a book yet" from "lost data".
        var be = new BlockEntityAkashicBookshelf(BlockPos.ZERO,
            HexBlocks.AKASHIC_BOOKSHELF.defaultBlockState());

        var tag = new CompoundTag();
        saveModData(be, tag);

        assertFalse(tag.contains(BlockEntityAkashicBookshelf.TAG_PATTERN),
            "empty shelf omits TAG_PATTERN");
        assertFalse(tag.contains(BlockEntityAkashicBookshelf.TAG_IOTA),
            "empty shelf omits TAG_IOTA");
        assertTrue(tag.contains(BlockEntityAkashicBookshelf.TAG_DUMMY),
            "empty shelf writes TAG_DUMMY sentinel");
    }

    @Test
    public void bookshelfRoundTripsPatternAndIotaTag() {
        // Populate via reflection because setNewMapping() does a world.setBlock() to swap the
        // HAS_BOOKS block-state — not available headless.
        var src = new BlockEntityAkashicBookshelf(BlockPos.ZERO,
            HexBlocks.AKASHIC_BOOKSHELF.defaultBlockState());
        var keyPattern = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        var iotaTag = IotaType.serialize(new DoubleIota(3.14));
        setField(src, "pattern", keyPattern);
        setField(src, "iotaTag", iotaTag);

        var savedTag = new CompoundTag();
        saveModData(src, savedTag);

        assertTrue(savedTag.contains(BlockEntityAkashicBookshelf.TAG_PATTERN, Tag.TAG_COMPOUND),
            "TAG_PATTERN present on populated shelf");
        assertTrue(savedTag.contains(BlockEntityAkashicBookshelf.TAG_IOTA, Tag.TAG_COMPOUND),
            "TAG_IOTA present on populated shelf");
        assertFalse(savedTag.contains(BlockEntityAkashicBookshelf.TAG_DUMMY),
            "no dummy sentinel when real data is stored");

        var dst = new BlockEntityAkashicBookshelf(BlockPos.ZERO,
            HexBlocks.AKASHIC_BOOKSHELF.defaultBlockState());
        loadModData(dst, savedTag);

        assertNotNull(dst.getPattern(), "pattern survived load");
        assertTrue(keyPattern.sigsEqual(dst.getPattern()),
            "pattern signature matches (sigsEqual is the lookup identity used by BlockAkashicRecord)");
        assertNotNull(dst.getIotaTag(), "iota tag survived load");
        // Deserialize the iota back out; null world is fine for DoubleIota.
        var backIota = IotaType.deserialize(dst.getIotaTag(), null);
        assertTrue(backIota instanceof DoubleIota, "got " + backIota.getClass().getSimpleName());
        assertEquals(3.14, ((DoubleIota) backIota).getDouble(), 1e-9);
    }

    @Test
    public void bookshelfLoadsNullFromDummyTag() {
        // Symmetric to the empty-save test: a dummy-only tag should leave the shelf empty.
        var dst = new BlockEntityAkashicBookshelf(BlockPos.ZERO,
            HexBlocks.AKASHIC_BOOKSHELF.defaultBlockState());
        // pre-seed to make sure load clears it
        setField(dst, "pattern", HexPattern.fromAngles("qaq", HexDir.NORTH_EAST));

        var tag = new CompoundTag();
        tag.putBoolean(BlockEntityAkashicBookshelf.TAG_DUMMY, false);
        loadModData(dst, tag);

        assertNull(dst.getPattern(), "pattern cleared by dummy-tag load");
        assertNull(dst.getIotaTag(), "iotaTag cleared by dummy-tag load");
    }

    // ---------- BlockEntityLookingImpetus ----------

    @Test
    public void lookingImpetusRoundTripsLookAmount() {
        // BlockEntityLookingImpetus extends BlockEntityAbstractImpetus; calling super.saveModData
        // writes TAG_MEDIA. With no executionState / pigment / error, the super call doesn't
        // touch this.level.registryAccess(), so the save runs headless.
        var src = new BlockEntityLookingImpetus(BlockPos.ZERO,
            HexBlocks.IMPETUS_LOOK.defaultBlockState());
        setField(src, "lookAmount", 7);

        var tag = new CompoundTag();
        saveModData(src, tag);

        assertTrue(tag.contains(BlockEntityLookingImpetus.TAG_LOOK_AMOUNT, Tag.TAG_INT),
            "TAG_LOOK_AMOUNT written as an int");
        assertEquals(7, tag.getInt(BlockEntityLookingImpetus.TAG_LOOK_AMOUNT));
        // super should always emit media, even at 0.
        assertTrue(tag.contains(BlockEntityAbstractImpetus.TAG_MEDIA, Tag.TAG_LONG),
            "super.saveModData emits TAG_MEDIA as a long");
        assertEquals(0L, tag.getLong(BlockEntityAbstractImpetus.TAG_MEDIA),
            "fresh impetus has 0 media");

        var dst = new BlockEntityLookingImpetus(BlockPos.ZERO,
            HexBlocks.IMPETUS_LOOK.defaultBlockState());
        loadModData(dst, tag);

        assertEquals(7, (int) getField(dst, "lookAmount"),
            "lookAmount preserved across save/load");
    }

    @Test
    public void lookingImpetusLoadsMediaFromTag() {
        // Load path must restore media even without a level — this is how BEs re-hydrate from
        // level.dat before their onLoad runs.
        var dst = new BlockEntityLookingImpetus(BlockPos.ZERO,
            HexBlocks.IMPETUS_LOOK.defaultBlockState());

        var tag = new CompoundTag();
        tag.putLong(BlockEntityAbstractImpetus.TAG_MEDIA, 123_456L);
        loadModData(dst, tag);

        assertEquals(123_456L, dst.getMedia(), "media loaded from TAG_MEDIA");
    }

    // ---------- BlockEntityRedstoneImpetus ----------

    @Test
    public void redstoneImpetusRoundTripsStoredPlayerUUID() {
        // Cleric impetus stores the bound player's UUID and resolvable profile. The profile path
        // uses ResolvableProfile.CODEC against NbtOps — no level needed.
        var src = new BlockEntityRedstoneImpetus(BlockPos.ZERO,
            HexBlocks.IMPETUS_REDSTONE.defaultBlockState());
        var playerUUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var profile = new GameProfile(playerUUID, "TestPlayer");
        src.setPlayer(profile, playerUUID);

        var tag = new CompoundTag();
        saveModData(src, tag);

        assertTrue(tag.contains(BlockEntityRedstoneImpetus.TAG_STORED_PLAYER, Tag.TAG_INT_ARRAY),
            "stored player UUID written as TAG_INT_ARRAY (UUID shape)");
        assertTrue(tag.contains(BlockEntityRedstoneImpetus.TAG_STORED_PLAYER_PROFILE, Tag.TAG_COMPOUND),
            "stored player profile written as compound (ResolvableProfile CODEC result)");

        var dst = new BlockEntityRedstoneImpetus(BlockPos.ZERO,
            HexBlocks.IMPETUS_REDSTONE.defaultBlockState());
        loadModData(dst, tag);

        assertEquals(playerUUID, getField(dst, "storedPlayer"),
            "storedPlayer UUID round-tripped");
        var loadedProfile = (GameProfile) getField(dst, "storedPlayerProfile");
        assertNotNull(loadedProfile, "storedPlayerProfile present after load");
        assertEquals(playerUUID, loadedProfile.getId(), "profile UUID preserved");
        assertEquals("TestPlayer", loadedProfile.getName(), "profile name preserved");
    }

    @Test
    public void redstoneImpetusClearsPlayerOnEmptyTag() {
        // Loading a tag without the stored-player keys clears the BE's stored player.
        var dst = new BlockEntityRedstoneImpetus(BlockPos.ZERO,
            HexBlocks.IMPETUS_REDSTONE.defaultBlockState());
        var playerUUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        dst.setPlayer(new GameProfile(playerUUID, "TestPlayer"), playerUUID);

        loadModData(dst, new CompoundTag());

        assertNull(getField(dst, "storedPlayer"),
            "storedPlayer cleared when tag lacks TAG_STORED_PLAYER");
        assertNull(getField(dst, "storedPlayerProfile"),
            "storedPlayerProfile cleared when tag lacks TAG_STORED_PLAYER_PROFILE");
    }

    // ---------- BlockEntityAbstractImpetus.getBounds ----------

    @Test
    public void getBoundsComputesInclusiveAabb() {
        // @Contract(pure = true) static — pure function that the ritual-circle sfx path uses
        // to decide which particles to render. The off-by-one on the upper corner (+1 on each
        // axis) is load-bearing because AABBs are half-open in MC.
        var poses = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(3, 64, 5),
            new BlockPos(-2, 70, 2));

        var bounds = invokeGetBounds(poses);

        assertEquals(-2.0, bounds.minX, 1e-9);
        assertEquals(64.0, bounds.minY, 1e-9);
        assertEquals(0.0, bounds.minZ, 1e-9);
        assertEquals(4.0, bounds.maxX, 1e-9, "maxX = 3 + 1 (half-open AABB)");
        assertEquals(71.0, bounds.maxY, 1e-9, "maxY = 70 + 1");
        assertEquals(6.0, bounds.maxZ, 1e-9, "maxZ = 5 + 1");
    }

    @Test
    public void getBoundsSinglePositionIsUnitCube() {
        var bounds = invokeGetBounds(List.of(new BlockPos(10, 20, 30)));
        assertEquals(10.0, bounds.minX, 1e-9);
        assertEquals(20.0, bounds.minY, 1e-9);
        assertEquals(30.0, bounds.minZ, 1e-9);
        assertEquals(11.0, bounds.maxX, 1e-9);
        assertEquals(21.0, bounds.maxY, 1e-9);
        assertEquals(31.0, bounds.maxZ, 1e-9);
    }

    private static AABB invokeGetBounds(List<BlockPos> poses) {
        try {
            Method m = BlockEntityAbstractImpetus.class.getDeclaredMethod("getBounds", List.class);
            m.setAccessible(true);
            return (AABB) m.invoke(null, poses);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- CircleExecutionState.save ----------

    @Test
    public void circleExecutionStateSaveHasExpectedKeys() {
        // The save side of CircleExecutionState is pure — it writes to a fresh CompoundTag. All
        // seven mandatory keys are part of the save-compat contract: if any go missing, an
        // ongoing ritual circle in a chunk-unloaded world silently dies on reload.
        //
        // 1.21 quirk: NbtUtils.writeBlockPos(BlockPos) returns an IntArrayTag (was CompoundTag
        // pre-1.21). So TAG_IMPETUS_POS / TAG_CURRENT_POS are IntArrayTags, not CompoundTags, and
        // the two list keys contain IntArrayTag elements. NbtUtils.readBlockPos(CompoundTag,
        // String) accepts either shape on the parent-compound path (line 188/204 of
        // CircleExecutionState) — but the list-element path (line 192-196) uses
        // getList(..., TAG_COMPOUND) as the type filter, which skips IntArrayTag entries. That
        // is a real save/load asymmetry: known/reached positions do NOT round-trip on 1.21.
        // Documented via the known-positions test below.
        var state = newCircleExecutionState(/*withOptional=*/false);
        var tag = state.save(RegistryAccess.EMPTY);

        assertTrue(tag.contains(CircleExecutionState.TAG_IMPETUS_POS, Tag.TAG_INT_ARRAY),
            "TAG_IMPETUS_POS present as IntArrayTag (1.21 writeBlockPos shape)");
        assertTrue(tag.contains(CircleExecutionState.TAG_IMPETUS_DIR, Tag.TAG_BYTE),
            "TAG_IMPETUS_DIR as byte ordinal");
        assertTrue(tag.contains(CircleExecutionState.TAG_KNOWN_POSITIONS, Tag.TAG_LIST),
            "TAG_KNOWN_POSITIONS as list");
        assertTrue(tag.contains(CircleExecutionState.TAG_REACHED_POSITIONS, Tag.TAG_LIST),
            "TAG_REACHED_POSITIONS as list");
        assertTrue(tag.contains(CircleExecutionState.TAG_CURRENT_POS, Tag.TAG_INT_ARRAY),
            "TAG_CURRENT_POS present as IntArrayTag");
        assertTrue(tag.contains(CircleExecutionState.TAG_ENTERED_FROM, Tag.TAG_BYTE),
            "TAG_ENTERED_FROM as byte ordinal");
        assertTrue(tag.contains(CircleExecutionState.TAG_IMAGE, Tag.TAG_COMPOUND),
            "TAG_IMAGE present (nested CastingImage tag)");

        // Optional keys absent when caster/pigment are null.
        assertFalse(tag.contains(CircleExecutionState.TAG_CASTER),
            "TAG_CASTER absent when caster==null");
        assertFalse(tag.contains(CircleExecutionState.TAG_PIGMENT),
            "TAG_PIGMENT absent when casterPigment==null");
    }

    @Test
    public void circleExecutionStateSaveOmitsOptionalWhenUnsetIncludesWhenSet() {
        // Verify the optional branches actually take effect — caster UUID + pigment tag only show
        // up when set, and their shapes are stable.
        var state = newCircleExecutionState(/*withOptional=*/true);
        var tag = state.save(RegistryAccess.EMPTY);

        assertTrue(tag.hasUUID(CircleExecutionState.TAG_CASTER),
            "TAG_CASTER stored as UUID (int-array pair)");
        assertTrue(tag.contains(CircleExecutionState.TAG_PIGMENT, Tag.TAG_COMPOUND),
            "TAG_PIGMENT stored as a compound (FrozenPigment.serializeToNBT)");

        // Caster UUID round-trips bit-for-bit.
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            tag.getUUID(CircleExecutionState.TAG_CASTER));
    }

    @Test
    public void circleExecutionStateSaveKnownPositionsHasEveryPos() {
        // Every BlockPos in knownPositions must show up in the saved ListTag. The save-side
        // iteration uses for-each over the set, and writeBlockPos returns an IntArrayTag on 1.21.
        // A regression where knownPositions was iterated wrong (e.g. only dumped one element)
        // would silently truncate the ritual shape and break re-closure detection on reload.
        //
        // Bug surfaced: CircleExecutionState.load uses getList(..., TAG_COMPOUND) as the type
        // filter (line 192) — that drops every IntArrayTag element. The matching fix is to use
        // TAG_INT_ARRAY for the type filter (and refactor readBlockPos to not expect a
        // sub-compound with a "pos" key). Leaving the bug intact here and asserting on the raw
        // tag shape so the test is stable if/when the load side is repaired.
        var state = newCircleExecutionState(/*withOptional=*/false);
        var tag = state.save(RegistryAccess.EMPTY);

        // Use getListTag directly — getList(key, TAG_COMPOUND) returns an empty list if element
        // types don't match, which is exactly the problem documented above.
        var raw = tag.get(CircleExecutionState.TAG_KNOWN_POSITIONS);
        assertNotNull(raw, "TAG_KNOWN_POSITIONS present");
        assertTrue(raw instanceof ListTag, "TAG_KNOWN_POSITIONS is a ListTag");
        var list = (ListTag) raw;
        assertEquals(3, list.size(),
            "three known positions written (impetus + two slates)");
        assertEquals(Tag.TAG_INT_ARRAY, list.getElementType(),
            "list elements are IntArrayTag (1.21 writeBlockPos shape)");
    }

    @Test
    public void circleExecutionStateSaveDirOrdinalsRoundTrip() {
        // Directions are stored as byte ordinals. The load side indexes Direction.values() —
        // so the save side must write the *ordinal*, not getId() or similar. Regression canary.
        var state = newCircleExecutionState(/*withOptional=*/false);
        var tag = state.save(RegistryAccess.EMPTY);

        assertEquals((byte) Direction.NORTH.ordinal(),
            tag.getByte(CircleExecutionState.TAG_IMPETUS_DIR),
            "impetus dir ordinal written verbatim");
        assertEquals((byte) Direction.NORTH.ordinal(),
            tag.getByte(CircleExecutionState.TAG_ENTERED_FROM),
            "enteredFrom ordinal written verbatim");
    }

    /**
     * CircleExecutionState's constructor is {@code protected} — we're in a different package, so
     * reflect to invoke it. The alternative (createNew) needs a live impetus BE + ServerLevel.
     */
    private static CircleExecutionState newCircleExecutionState(boolean withOptional) {
        BlockPos impetusPos = new BlockPos(0, 64, 0);
        Direction impetusDir = Direction.NORTH;
        Set<BlockPos> known = new HashSet<>();
        known.add(impetusPos);
        known.add(new BlockPos(0, 64, -1));
        known.add(new BlockPos(0, 64, -2));
        List<BlockPos> reached = new ArrayList<>();
        reached.add(impetusPos);
        BlockPos currentPos = new BlockPos(0, 64, -1);
        Direction enteredFrom = Direction.NORTH;
        CastingImage image = new CastingImage();

        UUID caster = null;
        FrozenPigment pigment = null;
        if (withOptional) {
            caster = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            pigment = new FrozenPigment(new ItemStack(HexItems.DEFAULT_PIGMENT), caster);
        }

        try {
            Constructor<CircleExecutionState> ctor = CircleExecutionState.class.getDeclaredConstructor(
                BlockPos.class, Direction.class, Set.class, List.class, BlockPos.class,
                Direction.class, CastingImage.class, UUID.class, FrozenPigment.class);
            ctor.setAccessible(true);
            return ctor.newInstance(impetusPos, impetusDir, known, reached, currentPos,
                enteredFrom, image, caster, pigment);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
