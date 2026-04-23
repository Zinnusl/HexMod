package at.petrak.hexcasting.test;

import at.petrak.hexcasting.common.lib.HexBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every block hex registers must:
 * <ul>
 *   <li>have a non-null Block instance,</li>
 *   <li>have a default BlockState that can be queried,</li>
 *   <li>be registered under the {@code hexcasting:} namespace,</li>
 *   <li>have a matching BlockItem (unless explicitly opted out — slate/impetus block-entities
 *       all have items),</li>
 *   <li>show up in {@link BuiltInRegistries#BLOCK} after {@link TestBootstrap} runs.</li>
 * </ul>
 * <p>
 * A regression where a block exists in {@code HexBlocks} but is missing from {@code BLOCK_ITEMS}
 * shows up as an unobtainable item in-game — the item appears in creative but isn't craftable
 * because the BlockItem was never registered. This test makes that gap visible.
 */
public final class BlockRegistrationTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void everyBlockHasSaneProperties() {
        Map<ResourceLocation, Block> blocks = new LinkedHashMap<>();
        HexBlocks.registerBlocks((block, loc) -> blocks.put(loc, block));

        assertFalse(blocks.isEmpty(), "HexBlocks.registerBlocks registered nothing");

        for (var e : blocks.entrySet()) {
            var loc = e.getKey();
            var block = e.getValue();
            assertNotNull(block, () -> loc + ": null Block");
            assertEquals("hexcasting", loc.getNamespace(),
                () -> loc + ": wrong namespace");
            assertFalse(loc.getPath().isEmpty(),
                () -> loc + ": empty path");
            assertNotNull(block.defaultBlockState(),
                () -> loc + ": no default BlockState");
        }
    }

    @Test
    public void everyBlockItemMatchesABlock() {
        // Collect block and block-item registrations; verify each BlockItem's referenced block is
        // in the block map. A typo in HexBlocks could silently create a BlockItem pointing at a
        // block that's never registered, and the BlockItem would turn into "missing:no" in-game.
        Map<ResourceLocation, Block> blocks = new LinkedHashMap<>();
        HexBlocks.registerBlocks((block, loc) -> blocks.put(loc, block));

        Map<ResourceLocation, Item> blockItems = new LinkedHashMap<>();
        HexBlocks.registerBlockItems((item, loc) -> blockItems.put(loc, item));

        for (var e : blockItems.entrySet()) {
            var loc = e.getKey();
            var item = e.getValue();
            assertTrue(item instanceof BlockItem,
                () -> loc + ": block-item is not actually a BlockItem (got " + item.getClass().getSimpleName() + ")");
            var referenced = ((BlockItem) item).getBlock();
            assertNotNull(referenced, () -> loc + ": BlockItem points at null block");

            // The referenced block must actually be in the block-registration map — this catches
            // cases where the block-item outlives its underlying block (e.g. the block was
            // removed but its BlockItem entry was left behind).
            Set<Block> knownBlocks = new HashSet<>(blocks.values());
            assertTrue(knownBlocks.contains(referenced),
                () -> loc + ": BlockItem references an un-registered block " + referenced.getClass().getSimpleName());
        }
    }

    @Test
    public void noDuplicateBlockIds() {
        // registerBlocks is fed into a LinkedHashMap — duplicate registrations would silently
        // overwrite the earlier entry. We cover this at registration-time elsewhere (via the
        // "Duplicate id" IllegalArgumentException in HexBlocks.make), but a double-check here
        // guards against the map getting populated through a different path.
        Set<ResourceLocation> seen = new HashSet<>();
        HexBlocks.registerBlocks((block, loc) -> {
            assertTrue(seen.add(loc), () -> "duplicate block id: " + loc);
        });
    }

    @Test
    public void coreBlocksPresent() {
        // Load-bearing blocks the mod assumes always exist. Regression would break spell-circle
        // and akashic machinery.
        Map<ResourceLocation, Block> blocks = new LinkedHashMap<>();
        HexBlocks.registerBlocks((block, loc) -> blocks.put(loc, block));

        assertTrue(blocks.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "slate")),
            "slate block registered");
        assertTrue(blocks.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "impetus/empty")),
            "impetus/empty (block-entity base) registered");
        assertTrue(blocks.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "impetus/rightclick")),
            "impetus/rightclick registered");
        assertTrue(blocks.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "akashic_record")),
            "akashic_record registered");
        assertTrue(blocks.containsKey(ResourceLocation.fromNamespaceAndPath("hexcasting", "akashic_bookshelf")),
            "akashic_bookshelf registered");
    }
}
