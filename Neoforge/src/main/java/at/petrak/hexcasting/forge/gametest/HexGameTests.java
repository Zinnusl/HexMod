package at.petrak.hexcasting.forge.gametest;

import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Functional assertions that reproduce bugs the boot-only smoketest missed.
 * <p>
 * Run with {@code make gametest} (Gradle task {@code :Neoforge:runGameTest}) or from
 * a dev client via {@code /test runall hexcasting}. Each test must pass within its
 * timeout to succeed; the runner exits non-zero if any assertion fires.
 * <p>
 * Tests live in an empty 3×3 structure. Structures that need setup call
 * {@link GameTestHelper#setBlock(BlockPos, net.minecraft.world.level.block.Block)}
 * on helper-relative coordinates to build their fixture programmatically; this
 * avoids shipping .nbt structure files for trivial layouts.
 */
@GameTestHolder("hexcasting")
@PrefixGameTestTemplate(false)
public final class HexGameTests {
    /** A minimal 3x3x3 empty structure used for setup-less tests. */
    private static final String EMPTY = "hexcasting:empty";

    private HexGameTests() {}

    /**
     * Regression for the staff crash: {@code LivingEntity#getAttributeValue} used to
     * throw {@code IllegalArgumentException: Can't find attribute hexcasting:feeble_mind}
     * because the attribute wasn't added to the Player's AttributeSupplier. With
     * EntityAttributeModificationEvent wired, every hex attribute must resolve.
     */
    @GameTest(template = EMPTY)
    public static void feebleMindAttributeIsWired(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        // Direct read — if the attribute isn't registered on the player this throws.
        double value = player.getAttributeValue(HexAttributes.holder(HexAttributes.FEEBLE_MIND));
        // Default range is [0,1] with default 0.
        helper.assertValueEqual(value, 0.0, "feeble_mind default value");
        // Also cover the other five hex attributes so a future registration regression
        // on any of them fails here first.
        player.getAttributeValue(HexAttributes.holder(HexAttributes.GRID_ZOOM));
        player.getAttributeValue(HexAttributes.holder(HexAttributes.SCRY_SIGHT));
        player.getAttributeValue(HexAttributes.holder(HexAttributes.MEDIA_CONSUMPTION_MODIFIER));
        player.getAttributeValue(HexAttributes.holder(HexAttributes.AMBIT_RADIUS));
        player.getAttributeValue(HexAttributes.holder(HexAttributes.SENTINEL_RADIUS));
        helper.succeed();
    }

    /**
     * Regression for the staff use() crash: invoking ItemStaff#use on a held staff
     * must not throw. Doesn't care about the interaction result — only that no
     * exception escapes.
     */
    @GameTest(template = EMPTY)
    public static void staffUseDoesNotCrash(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HexItems.STAFF_OAK));
        // ItemStaff.use opens the casting GUI on clients and returns pass on servers;
        // on a GameTest fake server-only player, the expected outcome is a clean call.
        ItemStack staff = player.getMainHandItem();
        staff.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.succeed();
    }

    /**
     * Regression for the amethyst-cluster crash: destroying a cluster triggers the
     * global loot modifier hexcasting:amethyst_cluster. That modifier used to throw
     * when the inject loot table couldn't be resolved; the try/catch in
     * ForgeHexAmethystLootMod now keeps the break path alive.
     */
    @GameTest(template = EMPTY)
    public static void amethystClusterDestroysCleanly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.AMETHYST_CLUSTER);
        // destroyBlock runs the full loot pipeline including global loot modifiers.
        helper.destroyBlock(pos);
        helper.succeed();
    }

    /**
     * Asserts hexcasting:amethyst_pillar is a registered shaped-crafting recipe that
     * actually matches 2x amethyst_block. Fails if the ingredient-wrapper data
     * migration regresses or the recipe goes missing from the registry.
     */
    @GameTest(template = EMPTY)
    public static void amethystPillarRecipeMatches(GameTestHelper helper) {
        var input = CraftingInput.of(1, 2, java.util.List.of(
            new ItemStack(Blocks.AMETHYST_BLOCK),
            new ItemStack(Blocks.AMETHYST_BLOCK)));
        var recipe = helper.getLevel().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "amethyst_pillar recipe must match 2x amethyst_block");
        var result = recipe.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(HexBlocks.AMETHYST_PILLAR.asItem()), "result is amethyst_pillar");
        helper.succeed();
    }
}
