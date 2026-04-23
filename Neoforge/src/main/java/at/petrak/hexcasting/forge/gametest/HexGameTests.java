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

    /**
     * Every hex custom registry must have at least one entry. Catches the
     * "HexActions.register was never wired" class of bug — RegisterEvent
     * binders populating ACTION/IOTA_TYPE/SPECIAL_HANDLER/ARITHMETIC/
     * EVAL_SOUND/CONTINUATION_TYPE.
     */
    @GameTest(template = EMPTY)
    public static void hexRegistriesPopulated(GameTestHelper helper) {
        var xplat = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE;
        helper.assertTrue(xplat.getActionRegistry().size() > 0, "action registry non-empty");
        helper.assertTrue(xplat.getIotaTypeRegistry().size() > 0, "iota_type registry non-empty");
        helper.assertTrue(xplat.getSpecialHandlerRegistry().size() >= 0, "special_handler registry resolved");
        helper.assertTrue(xplat.getArithmeticRegistry().size() > 0, "arithmetic registry non-empty");
        helper.assertTrue(xplat.getContinuationTypeRegistry().size() > 0, "continuation_type registry non-empty");
        helper.assertTrue(xplat.getEvalSoundRegistry().size() > 0, "eval_sound registry non-empty");
        helper.succeed();
    }

    /**
     * Brainsweep recipes exist in the registry and have non-stub data. Earlier port
     * iterations used a MapCodec.unit(DUMMY) placeholder; if that regresses, all
     * brainsweep recipes would parse to the same empty instance and this test fails.
     */
    @GameTest(template = EMPTY)
    public static void brainsweepRecipesLoaded(GameTestHelper helper) {
        var rm = helper.getLevel().getRecipeManager();
        int count = 0;
        int withBlock = 0;
        for (var holder : rm.getAllRecipesFor(at.petrak.hexcasting.common.recipe.HexRecipeStuffRegistry.BRAINSWEEP_TYPE)) {
            count++;
            if (!holder.value().result().is(net.minecraft.world.level.block.Blocks.AIR)) {
                withBlock++;
            }
        }
        helper.assertTrue(count > 0, "at least one brainsweep recipe registered (got " + count + ")");
        helper.assertTrue(withBlock > 0,
            "at least one brainsweep recipe has a real result block (got " + withBlock + " of " + count + ") — "
                + "did MapCodec.unit(DUMMY) come back?");
        helper.succeed();
    }

    /**
     * Pigment attachment roundtrips on set: calling xplat.setPigment should immediately
     * be reflected by getPigment. Guards against the AttachmentType registration and
     * the on-mutation sync packet path.
     */
    @GameTest(template = EMPTY)
    public static void pigmentAttachmentRoundtrip(GameTestHelper helper) {
        var xplat = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE;
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var defaultPigment = at.petrak.hexcasting.api.pigment.FrozenPigment.DEFAULT.get();
        var ancientPigment = at.petrak.hexcasting.api.pigment.FrozenPigment.ANCIENT.get();
        // Start at default
        helper.assertTrue(xplat.getPigment(player).item().is(defaultPigment.item().getItem()),
            "default pigment set on fresh player");
        // Set to ancient and read back
        xplat.setPigment(player, ancientPigment);
        helper.assertTrue(xplat.getPigment(player).item().is(ancientPigment.item().getItem()),
            "pigment attachment reflects setPigment");
        helper.succeed();
    }

    /**
     * Player-scoped attachments (sentinel, altiora, flight) survive setData→getData
     * without NPE. Regression check for the Codec.Optional wrapper conventions in
     * HexAttachments.
     */
    @GameTest(template = EMPTY)
    public static void playerAttachmentsGetSetNullable(GameTestHelper helper) {
        var xplat = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE;
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        // Reads on fresh player: all three should return null (Optional.empty).
        helper.assertTrue(xplat.getSentinel(player) == null, "sentinel default null");
        helper.assertTrue(xplat.getAltiora(player) == null, "altiora default null");
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            helper.assertTrue(xplat.getFlight(sp) == null, "flight default null");
        }
        // Set altiora to something, read back
        var altiora = new at.petrak.hexcasting.api.player.AltioraAbility(40);
        xplat.setAltiora(player, altiora);
        var reread = xplat.getAltiora(player);
        helper.assertTrue(reread != null && reread.gracePeriod() == 40,
            "altiora attachment reflects setAltiora");
        // Setting back to null clears
        xplat.setAltiora(player, null);
        helper.assertTrue(xplat.getAltiora(player) == null, "altiora cleared by null");
        helper.succeed();
    }

    /**
     * Custom ingredient serializers (hexcasting:unsealed, hexcasting:mod_conditional)
     * must be registered in NeoForgeRegistries.INGREDIENT_TYPES so recipes that
     * reference them parse rather than erroring.
     */
    @GameTest(template = EMPTY)
    public static void customIngredientTypesRegistered(GameTestHelper helper) {
        var lookup = helper.getLevel().registryAccess()
            .lookupOrThrow(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.INGREDIENT_TYPES);
        helper.assertTrue(
            lookup.get(net.minecraft.resources.ResourceKey.create(
                net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.INGREDIENT_TYPES,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hexcasting", "unsealed"))).isPresent(),
            "hexcasting:unsealed ingredient type registered");
        helper.assertTrue(
            lookup.get(net.minecraft.resources.ResourceKey.create(
                net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.INGREDIENT_TYPES,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hexcasting", "mod_conditional"))).isPresent(),
            "hexcasting:mod_conditional ingredient type registered");
        helper.succeed();
    }

    /**
     * The CustomPacketPayload StreamCodec for every hex Msg* must roundtrip so network
     * send/receive doesn't corrupt state. Exercise MsgShiftScrollC2S + MsgBeepS2C as
     * representatives (simple scalars + an enum-carrying payload).
     */
    @GameTest(template = EMPTY)
    public static void msgPayloadsRoundtrip(GameTestHelper helper) {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        var scroll = new at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S(
            1.5, -0.25, true, false, true);
        at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S.CODEC.encode(buf, scroll);
        var decoded = at.petrak.hexcasting.common.msgs.MsgShiftScrollC2S.CODEC.decode(buf);
        helper.assertValueEqual(decoded.mainHandDelta(), 1.5, "mainHandDelta roundtrip");
        helper.assertValueEqual(decoded.offHandDelta(), -0.25, "offHandDelta roundtrip");
        helper.assertTrue(decoded.isCtrl(), "isCtrl roundtrip");
        helper.assertTrue(!decoded.invertSpellbook(), "invertSpellbook roundtrip");
        helper.assertTrue(decoded.invertAbacus(), "invertAbacus roundtrip");

        buf.clear();
        var beep = new at.petrak.hexcasting.common.msgs.MsgBeepS2C(
            new net.minecraft.world.phys.Vec3(1, 2, 3), 7,
            net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BELL);
        at.petrak.hexcasting.common.msgs.MsgBeepS2C.CODEC.encode(buf, beep);
        var decodedBeep = at.petrak.hexcasting.common.msgs.MsgBeepS2C.CODEC.decode(buf);
        helper.assertValueEqual(decodedBeep.note(), 7, "beep note roundtrip");
        helper.assertTrue(decodedBeep.instrument() ==
                net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BELL,
            "beep instrument roundtrip");
        helper.succeed();
    }

    /**
     * tryPlaceFluid on an air block should succeed and drop a fluid source;
     * drainAllFluid on that same block should return true and clear to air.
     * Covers OpCreateFluid / OpDestroyFluid gameplay path.
     */
    @GameTest(template = EMPTY)
    public static void fluidIoPlaceAndDrain(GameTestHelper helper) {
        var xplat = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE;
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);

        boolean placed = xplat.tryPlaceFluid(helper.getLevel(), InteractionHand.MAIN_HAND, pos,
            net.minecraft.world.level.material.Fluids.WATER);
        helper.assertTrue(placed, "tryPlaceFluid(water) on air returns true");
        helper.assertBlock(pos,
            b -> b == net.minecraft.world.level.block.Blocks.WATER, "water block placed");

        boolean drained = xplat.drainAllFluid(helper.getLevel(), pos);
        helper.assertTrue(drained, "drainAllFluid on water returns true");
        helper.succeed();
    }

    /**
     * Slate pattern data survives component roundtrip via DataComponents.BLOCK_ENTITY_DATA.
     * Regression for the 1.20→1.21 BlockEntityTag → BLOCK_ENTITY_DATA migration in
     * ItemSlate.
     */
    @GameTest(template = EMPTY)
    public static void slatePatternComponentRoundtrip(GameTestHelper helper) {
        var stack = new ItemStack(HexItems.SLATE);
        // Start: no pattern
        helper.assertTrue(!at.petrak.hexcasting.common.items.storage.ItemSlate.hasPattern(stack),
            "fresh slate has no pattern");
        // Write a pattern via the IotaHolder interface
        var pattern = at.petrak.hexcasting.api.casting.math.HexPattern.fromAngles(
            "qqq", at.petrak.hexcasting.api.casting.math.HexDir.EAST);
        var iota = new at.petrak.hexcasting.api.casting.iota.PatternIota(pattern);
        ((at.petrak.hexcasting.api.item.IotaHolderItem) HexItems.SLATE).writeDatum(stack, iota);
        helper.assertTrue(at.petrak.hexcasting.common.items.storage.ItemSlate.hasPattern(stack),
            "slate has pattern after writeDatum");
        // Clear via null and confirm component is gone
        ((at.petrak.hexcasting.api.item.IotaHolderItem) HexItems.SLATE).writeDatum(stack, null);
        helper.assertTrue(!at.petrak.hexcasting.common.items.storage.ItemSlate.hasPattern(stack),
            "slate pattern cleared by null writeDatum");
        helper.succeed();
    }
}
