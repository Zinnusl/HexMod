package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.block.circle.BlockCircleComponent;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.blocks.circles.BlockSlate;
import at.petrak.hexcasting.common.items.storage.ItemSlate;
import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ritual circles are made of impetus + slate blocks; the impetus walks slate to slate, evaluating
 * each slate's stored pattern through a shared {@link at.petrak.hexcasting.api.casting.eval.vm.CastingImage}.
 * Full circle execution needs a live {@code ServerLevel}, so we don't exercise that path here.
 * What we can verify without a server:
 * <ul>
 *   <li>{@link BlockSlate} block-state properties — FACING, ATTACH_FACE, WATERLOGGED must all
 *       exist on the default state so {@code BlockItem} placement doesn't NPE.</li>
 *   <li>{@link BlockSlate#normalDir} — the core rule that drives {@code ICircleComponent} pathing.
 *       A slate on the floor has normal UP; on a wall, normal is the FACING direction.</li>
 *   <li>{@link BlockSlate#possibleExitDirections} — must never include the slate's normal, because
 *       you can't exit "into" the surface the slate attaches to. An off-by-one here would let the
 *       impetus's circle walker enter/exit through the back face.</li>
 *   <li>{@link ItemSlate} NBT round-trip via {@code writeDatum} / {@code readIotaTag} — the
 *       block-entity-tag path ({@code minecraft:block_entity_data}) has changed shape on 1.21.</li>
 *   <li>{@link FrozenPigment} NBT round-trip via {@link RegistryAccess#EMPTY} — the FrozenPigment
 *       in an impetus and stored on players must survive a save/load cycle.</li>
 * </ul>
 */
public final class RitualCircleTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void slateHasExpectedProperties() {
        // Regression target: if createBlockStateDefinition forgets to add a property to the
        // StateDefinition.Builder, the BlockState will still construct but any .getValue() on
        // the missing property NPEs. This catches the wiring at construction time.
        var slate = (BlockSlate) HexBlocks.SLATE;
        var defaultState = slate.defaultBlockState();
        assertNotNull(defaultState.getValue(BlockSlate.FACING), "FACING present");
        assertNotNull(defaultState.getValue(BlockSlate.ATTACH_FACE), "ATTACH_FACE present");
        assertNotNull(defaultState.getValue(BlockSlate.WATERLOGGED), "WATERLOGGED present");
        assertNotNull(defaultState.getValue(BlockCircleComponent.ENERGIZED), "ENERGIZED present");

        assertFalse(defaultState.getValue(BlockSlate.WATERLOGGED), "default is dry");
        assertFalse(defaultState.getValue(BlockCircleComponent.ENERGIZED), "default is unpowered");
    }

    @Test
    public void slateNormalDirMatchesAttachFace() {
        // Floor slate → normal UP. Ceiling → DOWN. Wall → FACING (the "front" of the slate).
        var slate = (BlockSlate) HexBlocks.SLATE;

        var floor = slate.defaultBlockState()
            .setValue(BlockSlate.ATTACH_FACE, AttachFace.FLOOR)
            .setValue(BlockSlate.FACING, Direction.NORTH);
        assertEquals(Direction.UP, slate.normalDir(BlockPos.ZERO, floor, null, 0),
            "floor slate normal is UP");

        var ceiling = floor.setValue(BlockSlate.ATTACH_FACE, AttachFace.CEILING);
        assertEquals(Direction.DOWN, slate.normalDir(BlockPos.ZERO, ceiling, null, 0),
            "ceiling slate normal is DOWN");

        var wallNorth = floor.setValue(BlockSlate.ATTACH_FACE, AttachFace.WALL)
            .setValue(BlockSlate.FACING, Direction.NORTH);
        assertEquals(Direction.NORTH, slate.normalDir(BlockPos.ZERO, wallNorth, null, 0),
            "wall slate normal follows FACING");
    }

    @Test
    public void slateExitDirectionsExcludeNormal() {
        // The circle walker feeds exitDirs to the next-block lookup — if the normal direction is
        // included, the walker could cross out of the circle plane. Since possibleExitDirections
        // iterates every block in the ritual, any regression here would have cascading effects.
        var slate = (BlockSlate) HexBlocks.SLATE;
        var state = slate.defaultBlockState()
            .setValue(BlockSlate.ATTACH_FACE, AttachFace.FLOOR)
            .setValue(BlockSlate.FACING, Direction.NORTH);

        EnumSet<Direction> exits = slate.possibleExitDirections(BlockPos.ZERO, state, null);
        assertFalse(exits.contains(Direction.UP), "floor slate must not exit up (into the normal)");
        // All other five directions should be valid exits.
        assertEquals(5, exits.size(), "floor slate exits 5 directions");
        assertTrue(exits.contains(Direction.NORTH));
        assertTrue(exits.contains(Direction.SOUTH));
        assertTrue(exits.contains(Direction.EAST));
        assertTrue(exits.contains(Direction.WEST));
        assertTrue(exits.contains(Direction.DOWN), "floor slate exits down into the floor it's sitting on");
    }

    @Test
    public void slateCanEnterFromNonNormalOpposite() {
        // The rule "canEnterFromDirection: enterDir != normalDir.getOpposite()" lets the walker
        // enter a floor slate from anywhere except DOWN (because DOWN is the opposite of the UP
        // normal). Coming from DOWN would mean the walker passed through the floor.
        var slate = (BlockSlate) HexBlocks.SLATE;
        var floor = slate.defaultBlockState()
            .setValue(BlockSlate.ATTACH_FACE, AttachFace.FLOOR);

        assertTrue(slate.canEnterFromDirection(Direction.NORTH, BlockPos.ZERO, floor, null),
            "walker can enter floor slate from NORTH");
        assertTrue(slate.canEnterFromDirection(Direction.UP, BlockPos.ZERO, floor, null),
            "walker can enter floor slate from UP (the normal side)");
        assertFalse(slate.canEnterFromDirection(Direction.DOWN, BlockPos.ZERO, floor, null),
            "walker cannot enter floor slate from DOWN (opposite of normal = through the floor)");
    }

    @Test
    public void slateItemStoresAndReadsPattern() {
        // Slates-as-items carry their pattern via the minecraft:block_entity_data component
        // bridge. This is the failure mode we actually hit in-game during the port: pattern
        // stored, then lost on re-read. Directly guards against regressions in ItemSlate's
        // BlockEntityTag bridge.
        var slate = (ItemSlate) HexItems.SLATE;
        var pattern = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
        ItemStack stack = new ItemStack(HexItems.SLATE);
        slate.writeDatum(stack, new PatternIota(pattern));

        var tag = slate.readIotaTag(stack);
        assertNotNull(tag, "slate should hold an iota tag after writeDatum");

        // The iota is nested inside the BlockEntityTag — verify by running deserialize.
        var back = at.petrak.hexcasting.api.casting.iota.IotaType.deserialize(tag, null);
        assertTrue(back instanceof PatternIota, "got " + back.getClass().getSimpleName());
        var backPat = ((PatternIota) back).getPattern();
        assertEquals(pattern.anglesSignature(), backPat.anglesSignature(), "angles survive BlockEntityTag round-trip");
        assertEquals(pattern.getStartDir(), backPat.getStartDir(), "startDir survives");
    }

    @Test
    public void frozenPigmentNBTRoundtrip() {
        // Pigments ride on impetuses and players; the NBT path needs a HolderLookup.Provider on
        // 1.21 (for ItemStack.save). RegistryAccess.EMPTY is sufficient for non-registry stacks.
        var owner = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var stack = new ItemStack(HexItems.DEFAULT_PIGMENT);
        var original = new FrozenPigment(stack, owner);

        var tag = original.serializeToNBT(RegistryAccess.EMPTY);
        var back = FrozenPigment.fromNBT(tag, RegistryAccess.EMPTY);

        assertEquals(owner, back.owner(), "owner UUID preserved");
        assertEquals(stack.getItem(), back.item().getItem(), "item type preserved");
        assertEquals(stack.getCount(), back.item().getCount(), "stack count preserved");
    }

    @Test
    public void frozenPigmentDefaultOnEmpty() {
        // Empty tag → fall back to the DEFAULT pigment. This is the contract the game relies on
        // when a player has no pigment set.
        var back = FrozenPigment.fromNBT(new net.minecraft.nbt.CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(Util.NIL_UUID, back.owner(), "default pigment has NIL owner");
    }
}
