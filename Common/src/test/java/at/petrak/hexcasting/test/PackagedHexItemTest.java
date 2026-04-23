package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.item.VariantItem;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.magic.ItemAncientCypher;
import at.petrak.hexcasting.common.items.magic.ItemArtifact;
import at.petrak.hexcasting.common.items.magic.ItemCypher;
import at.petrak.hexcasting.common.items.magic.ItemMediaBattery;
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder;
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex;
import at.petrak.hexcasting.common.items.magic.ItemTrinket;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// JUnit 5.6 has no assertInstanceOf — use assertTrue(x instanceof Y) throughout.

/**
 * Packaged-hex items (cypher, ancient cypher, trinket, artifact) and media batteries all ride the
 * same NBT path through {@link ItemMediaHolder} + {@link ItemPackagedHex}. On 1.21 that path now
 * bridges through {@code minecraft:custom_data}, and {@code NBTHelper.getCompound} returns a copy
 * — a regression in the write-back dance in {@code writeHex} / {@code clearHex} would silently
 * lose player programs.
 * <p>
 * These tests exercise the round-trip at the public API surface each subclass inherits from
 * {@link ItemPackagedHex}. They avoid {@code use()} (which needs a live ServerLevel + Player) and
 * {@link ItemPackagedHex#cooldown()} (which would NPE against the null {@code HexConfig.common()}
 * stub) — both get implicit coverage through gameplay. What matters for persistence is the
 * serialize/deserialize round-trip plus the class-level policy flags ({@code breakAfterDepletion},
 * {@code canRecharge}, {@code canProvideMedia}).
 */
public final class PackagedHexItemTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    private static List<Iota> makeProgram() {
        return List.of(
            new PatternIota(HexPattern.fromAngles("qaq", HexDir.NORTH_EAST)),
            new DoubleIota(3.14),
            new BooleanIota(true),
            new NullIota()
        );
    }

    private static void assertProgramsEqual(List<Iota> expected, List<Iota> actual) {
        assertEquals(expected.size(), actual.size(), "program size preserved");
        for (int i = 0; i < expected.size(); i++) {
            Iota want = expected.get(i);
            Iota got = actual.get(i);
            assertEquals(want.getClass(), got.getClass(),
                "iota " + i + " class: expected " + want.getClass().getSimpleName()
                    + " got " + got.getClass().getSimpleName());
            if (want instanceof DoubleIota w) {
                assertEquals(w.getDouble(), ((DoubleIota) got).getDouble(), 0.0,
                    "iota " + i + " double value");
            } else if (want instanceof BooleanIota w) {
                assertEquals(w.getBool(), ((BooleanIota) got).getBool(),
                    "iota " + i + " boolean value");
            } else if (want instanceof PatternIota w) {
                assertEquals(w.getPattern().anglesSignature(),
                    ((PatternIota) got).getPattern().anglesSignature(),
                    "iota " + i + " pattern angles");
                assertEquals(w.getPattern().getStartDir(),
                    ((PatternIota) got).getPattern().getStartDir(),
                    "iota " + i + " pattern start dir");
            }
        }
    }

    // ---- ItemPackagedHex.writeHex / getHex / hasHex / clearHex ------------------------------

    @Test
    public void cypherWriteHexRoundtrip() {
        // The core persistence contract: writeHex must put both patterns + pigment + media into
        // CUSTOM_DATA, and getHex must read them back as Iota instances. Primitive iotas accept
        // a null ServerLevel, so we don't need a world here.
        var cypher = (ItemCypher) HexItems.CYPHER;
        ItemStack stack = new ItemStack(cypher);
        assertFalse(cypher.hasHex(stack), "fresh stack has no hex");
        assertNull(cypher.getHex(stack, null), "getHex on empty stack returns null");

        var program = makeProgram();
        cypher.writeHex(stack, program, null, 12345L);

        assertTrue(cypher.hasHex(stack), "hasHex flips true after writeHex");
        var back = cypher.getHex(stack, null);
        assertNotNull(back, "getHex reads back the program");
        assertProgramsEqual(program, back);

        // writeHex stores media via ItemMediaHolder.withMedia — both current and max end up equal
        // to the argument, as required by the UI for bar-fullness display.
        assertEquals(12345L, cypher.getMedia(stack), "media tag set");
        assertEquals(12345L, cypher.getMaxMedia(stack), "max media tag set");
    }

    @Test
    public void cypherWriteHexWithPigment() {
        // Pigment round-trip: when writeHex gets a non-null FrozenPigment, it must be serialized
        // under TAG_PIGMENT and come back as a non-null FrozenPigment with the same owner +
        // pigment item.
        var cypher = (ItemCypher) HexItems.CYPHER;
        ItemStack stack = new ItemStack(cypher);
        var owner = UUID.fromString("deadbeef-0000-0000-0000-000000000001");
        var pigment = new FrozenPigment(new ItemStack(HexItems.DEFAULT_PIGMENT), owner);

        cypher.writeHex(stack, makeProgram(), pigment, 100L);
        var back = cypher.getPigment(stack);
        assertNotNull(back, "getPigment returns stored pigment");
        assertEquals(owner, back.owner(), "pigment owner preserved");
        assertEquals(HexItems.DEFAULT_PIGMENT, back.item().getItem(), "pigment item preserved");
    }

    @Test
    public void cypherPigmentNullWhenNotWritten() {
        // writeHex(null pigment) must NOT write a pigment compound — and getPigment must report
        // null. The ItemUse path branches on pigment == null to fall back to the caster's
        // pigment, so accidentally writing a default here would change the contract.
        var cypher = (ItemCypher) HexItems.CYPHER;
        ItemStack stack = new ItemStack(cypher);
        cypher.writeHex(stack, makeProgram(), null, 50L);
        assertNull(cypher.getPigment(stack), "null pigment stays null");
        assertFalse(NBTHelper.hasCompound(stack, ItemPackagedHex.TAG_PIGMENT),
            "TAG_PIGMENT not present when pigment is null");
    }

    @Test
    public void clearHexRemovesAllFields() {
        // clearHex removes TAG_PROGRAM + TAG_PIGMENT + TAG_MEDIA + TAG_MAX_MEDIA. After clear,
        // the stack must look brand-new: hasHex false, getHex null, no pigment, no media.
        var cypher = (ItemCypher) HexItems.CYPHER;
        ItemStack stack = new ItemStack(cypher);
        var pigment = new FrozenPigment(new ItemStack(HexItems.DEFAULT_PIGMENT), Util.NIL_UUID);
        cypher.writeHex(stack, makeProgram(), pigment, 999L);

        cypher.clearHex(stack);

        assertFalse(cypher.hasHex(stack), "hasHex is false after clearHex");
        assertNull(cypher.getHex(stack, null), "getHex is null after clearHex");
        assertNull(cypher.getPigment(stack), "getPigment is null after clearHex");
        assertFalse(NBTHelper.hasList(stack, ItemPackagedHex.TAG_PROGRAM, Tag.TAG_COMPOUND),
            "TAG_PROGRAM removed");
        assertFalse(NBTHelper.hasCompound(stack, ItemPackagedHex.TAG_PIGMENT),
            "TAG_PIGMENT removed");
        assertFalse(NBTHelper.hasLong(stack, ItemMediaHolder.TAG_MEDIA),
            "TAG_MEDIA removed");
        assertFalse(NBTHelper.hasLong(stack, ItemMediaHolder.TAG_MAX_MEDIA),
            "TAG_MAX_MEDIA removed");
    }

    @Test
    public void cypherEmptyProgramStillHasHex() {
        // An empty program is still a program — hasHex checks list *presence*, not its emptiness.
        // Regression: if hasHex used size>0 it would skip broadcasting the HAS_PATTERNS predicate
        // on items the player cleared and re-wrote with a blank list.
        var cypher = (ItemCypher) HexItems.CYPHER;
        ItemStack stack = new ItemStack(cypher);
        cypher.writeHex(stack, List.of(), null, 0L);
        assertTrue(cypher.hasHex(stack), "empty program still counts as having a hex");
        var back = cypher.getHex(stack, null);
        assertNotNull(back, "getHex returns a non-null empty list");
        assertEquals(0, back.size(), "empty list preserved");
    }

    @Test
    public void cypherStackCopyPreservesHex() {
        // Crafting recipes use ItemStack.copy(); everything in custom_data must follow. The
        // packaged hex is the most fragile case because it nests a ListTag of compound tags.
        var cypher = (ItemCypher) HexItems.CYPHER;
        ItemStack stack = new ItemStack(cypher);
        var program = makeProgram();
        var pigment = new FrozenPigment(new ItemStack(HexItems.DEFAULT_PIGMENT), Util.NIL_UUID);
        cypher.writeHex(stack, program, pigment, 777L);

        ItemStack copy = stack.copy();
        assertTrue(cypher.hasHex(copy), "copy still has the hex");
        assertProgramsEqual(program, cypher.getHex(copy, null));
        assertEquals(777L, cypher.getMedia(copy), "media survives copy");
        assertEquals(777L, cypher.getMaxMedia(copy), "max media survives copy");
        var copyPigment = cypher.getPigment(copy);
        assertNotNull(copyPigment, "pigment survives copy");
        assertEquals(Util.NIL_UUID, copyPigment.owner(), "pigment owner survives copy");
    }

    // ---- Per-subclass policy flags --------------------------------------------------------

    @Test
    public void cypherBreaksAfterDepletion() {
        // Cypher-specific contract: ItemPackagedHex.use branches on breakAfterDepletion() to
        // shrink the stack. Trinkets + artifacts must NOT break. These are the three class-level
        // values that drive durability UX.
        var cypher = (ItemCypher) HexItems.CYPHER;
        assertTrue(cypher.breakAfterDepletion(), "cypher breaks on depletion");
        // canRecharge = !breakAfterDepletion, so cyphers are single-use.
        assertFalse(cypher.canRecharge(new ItemStack(cypher)), "cyphers cannot be recharged");
    }

    @Test
    public void trinketDoesNotBreakAndDoesNotDrawFromInventory() {
        var trinket = (ItemTrinket) HexItems.TRINKET;
        assertFalse(trinket.breakAfterDepletion(), "trinket does not break");
        assertTrue(trinket.canRecharge(new ItemStack(trinket)), "trinket is rechargeable");
        assertFalse(trinket.canDrawMediaFromInventory(new ItemStack(trinket)),
            "trinket does not draw from inventory");
        assertFalse(trinket.canProvideMedia(new ItemStack(trinket)),
            "trinket doesn't provide media to other items");
    }

    @Test
    public void artifactDrawsFromInventory() {
        // Artifact is the only packaged hex item that can pull media from the player's
        // inventory to refill itself — canDrawMediaFromInventory==true is what drives that
        // behavior in the live cast path.
        var artifact = (ItemArtifact) HexItems.ARTIFACT;
        assertFalse(artifact.breakAfterDepletion(), "artifact doesn't break");
        assertTrue(artifact.canRecharge(new ItemStack(artifact)), "artifact is rechargeable");
        assertTrue(artifact.canDrawMediaFromInventory(new ItemStack(artifact)),
            "artifact draws media from inventory");
        assertFalse(artifact.canProvideMedia(new ItemStack(artifact)),
            "artifact doesn't feed other media consumers");
    }

    @Test
    public void ancientCypherExtendsCypherAndPreservesBreakBehavior() {
        // ItemAncientCypher extends ItemCypher — it shares the break-on-depletion rule. Its
        // only observable change is that clearHex also scrubs TAG_HEX_NAME (test below).
        var ancient = (ItemAncientCypher) HexItems.ANCIENT_CYPHER;
        assertTrue(ancient instanceof ItemCypher,
            "ancient cypher inherits the cypher break-on-depletion behavior");
        assertTrue(ancient.breakAfterDepletion(), "ancient cypher breaks like a regular cypher");
        assertFalse(ancient.canRecharge(new ItemStack(ancient)),
            "ancient cypher isn't rechargeable");
    }

    @Test
    public void ancientCypherClearHexRemovesHexName() {
        // ItemAncientCypher overrides clearHex to ALSO remove TAG_HEX_NAME. Without this the
        // scrubbed cypher would still display the old title in its tooltip — a visible regression.
        var ancient = (ItemAncientCypher) HexItems.ANCIENT_CYPHER;
        ItemStack stack = new ItemStack(ancient);
        ancient.writeHex(stack, makeProgram(), null, 100L);
        NBTHelper.putString(stack, ItemAncientCypher.TAG_HEX_NAME, "hexcasting.spell.flight");
        assertTrue(NBTHelper.hasString(stack, ItemAncientCypher.TAG_HEX_NAME));

        ancient.clearHex(stack);

        assertFalse(NBTHelper.hasString(stack, ItemAncientCypher.TAG_HEX_NAME),
            "hex name must be cleared with the rest of the hex");
        assertFalse(ancient.hasHex(stack), "program cleared too (super.clearHex ran)");
    }

    // ---- ItemMediaBattery -----------------------------------------------------------------

    @Test
    public void batteryWithMediaSetsTags() {
        // withMedia is the static helper HexItems.BATTERY_*_STACK suppliers use at mod init.
        // Regression here would make every hand-out battery show a wrong bar + wrong capacity.
        ItemStack stack = new ItemStack(HexItems.BATTERY);
        ItemMediaBattery.withMedia(stack, 500L, 1000L);
        assertEquals(500L, HexItems.BATTERY.getMedia(stack), "current media set");
        assertEquals(1000L, HexItems.BATTERY.getMaxMedia(stack), "max media set");
        assertTrue(HexItems.BATTERY.isBarVisible(stack),
            "bar visible when maxMedia > 0");
    }

    @Test
    public void batteryWithMediaReturnsSameStackInstance() {
        // withMedia returns the mutated stack so it can be chained. The return value must be
        // the SAME reference, not a copy — any copy-semantic regression would decouple the
        // caller's reference from the one we mutated.
        ItemStack stack = new ItemStack(HexItems.BATTERY);
        ItemStack returned = ItemMediaBattery.withMedia(stack, 10L, 100L);
        assertSame(stack, returned, "withMedia returns the argument stack");
    }

    @Test
    public void batteryWithMediaNoOpOnNonMediaHolder() {
        // withMedia branches on ItemMediaHolder instanceof — passing any other stack must not
        // crash and must not touch its NBT. The BATTERY_*_STACK supplier relies on this being a
        // safe no-op when called on the wrong item during a data-pack hiccup.
        ItemStack stack = new ItemStack(HexItems.FOCUS);
        ItemMediaBattery.withMedia(stack, 42L, 84L);
        assertFalse(NBTHelper.hasLong(stack, ItemMediaHolder.TAG_MEDIA),
            "non-media-holder stack doesn't gain TAG_MEDIA");
    }

    @Test
    public void batteryCanProvideAndRecharge() {
        // Battery policy: provides media TO other items, AND is rechargeable. This distinguishes
        // it from packaged hexes (which only recharge themselves, never feed others).
        var battery = (ItemMediaBattery) HexItems.BATTERY;
        ItemStack stack = new ItemStack(battery);
        assertTrue(battery.canProvideMedia(stack), "battery provides media");
        assertTrue(battery.canRecharge(stack), "battery rechargeable");
    }

    @Test
    public void batteryBarVisibilityFollowsMaxMedia() {
        // isBarVisible is keyed on maxMedia > 0. A freshly-constructed battery with no NBT has
        // max=0, so the durability bar should be hidden.
        ItemStack fresh = new ItemStack(HexItems.BATTERY);
        assertFalse(HexItems.BATTERY.isBarVisible(fresh),
            "bar hidden on a zero-capacity battery");

        ItemMediaBattery.withMedia(fresh, 0L, 500L);
        assertTrue(HexItems.BATTERY.isBarVisible(fresh),
            "bar visible once max media is set, even if current is 0");
    }

    @Test
    public void mediaHolderSetMediaClamped() {
        // ItemMediaHolder.setMedia clamps into [0, maxMedia]. A negative set must become 0; an
        // over-set must become maxMedia. This is what prevents overdraft when a spell refunds
        // more than the stack started with.
        ItemStack stack = new ItemStack(HexItems.BATTERY);
        ItemMediaBattery.withMedia(stack, 100L, 500L);
        HexItems.BATTERY.setMedia(stack, 300L);
        assertEquals(300L, HexItems.BATTERY.getMedia(stack), "in-range set persists");

        HexItems.BATTERY.setMedia(stack, -1L);
        assertEquals(0L, HexItems.BATTERY.getMedia(stack), "negative set clamped to 0");

        HexItems.BATTERY.setMedia(stack, 9_999L);
        assertEquals(500L, HexItems.BATTERY.getMedia(stack),
            "over-set clamped to maxMedia");
    }

    // ---- Cross-class: writeHex replaces, not appends --------------------------------------

    @Test
    public void writeHexReplacesEarlierProgram() {
        // A second writeHex must REPLACE the earlier patterns list — patterns don't concatenate.
        // Regression here from a putList that appended rather than replacing would bloat the
        // program every time a player updated it.
        var trinket = (ItemTrinket) HexItems.TRINKET;
        ItemStack stack = new ItemStack(trinket);
        trinket.writeHex(stack, makeProgram(), null, 10L);
        var small = List.<Iota>of(new DoubleIota(1.0));
        trinket.writeHex(stack, small, null, 20L);

        var back = trinket.getHex(stack, null);
        assertNotNull(back);
        assertEquals(1, back.size(), "second writeHex replaces the list");
        assertEquals(1.0, ((DoubleIota) back.get(0)).getDouble(), 0.0);
        assertEquals(20L, trinket.getMedia(stack), "media updated too");
    }

    // ---- ItemSpellbook: more NBT coverage beyond ItemNBTRoundtripTest ---------------------

    @Test
    public void spellbookHighestPageTracksMaxWrittenPage() {
        // highestPage scans TAG_PAGES keys and returns max(int). Tooltip uses it for "page X of Y".
        // Regression: if writeDatum put keys under a non-numeric name, highestPage would return 0
        // and the tooltip would render "page 1 of 0" — visible breakage.
        //
        // Quirk: on an EMPTY book, getPage() returns the `ifEmpty` default regardless of
        // TAG_SELECTED_PAGE. So the first write must land on page 1 and the higher pages follow.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        assertEquals(0, ItemSpellbook.highestPage(stack), "empty book => highest 0");

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(1.0));
        assertEquals(1, ItemSpellbook.highestPage(stack), "after first write => highest 1");

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 3);
        book.writeDatum(stack, new DoubleIota(3.0));
        assertEquals(3, ItemSpellbook.highestPage(stack), "wrote page 3 => highest 3");

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        book.writeDatum(stack, new DoubleIota(2.0));
        assertEquals(3, ItemSpellbook.highestPage(stack),
            "writing an earlier page doesn't reduce highest");
    }

    @Test
    public void spellbookArePagesEmptyReflectsTagState() {
        // arePagesEmpty is the branch in getPage() that chooses ifEmpty over TAG_SELECTED_PAGE.
        // This is why a fresh spellbook always reports page=1: no TAG_PAGES yet.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        assertTrue(ItemSpellbook.arePagesEmpty(stack), "fresh book has no pages");

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(42.0));
        assertFalse(ItemSpellbook.arePagesEmpty(stack),
            "after write, TAG_PAGES is populated");
    }

    @Test
    public void spellbookWriteDatumNullRemovesPage() {
        // Writing null to a page deletes just that page (keeping other pages intact). Because
        // NBTHelper.getCompound returns a copy on 1.21, the put-back step in writeDatum is
        // fragile — this test catches any regression that forgets to re-putCompound.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(1.0));
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        book.writeDatum(stack, new DoubleIota(2.0));

        // Delete page 1, leaving page 2 intact.
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, null);
        assertNull(book.readIotaTag(stack), "page 1 cleared");

        // Page 2's iota must still be readable.
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        var page2 = book.readIotaTag(stack);
        assertNotNull(page2, "page 2 survives page 1 deletion");
        assertEquals(2.0, ((DoubleIota) IotaType.deserialize(page2, null)).getDouble(), 0.0);
    }

    @Test
    public void spellbookSealingRoundTrips() {
        // setSealed(true) persists; isSealed reads the same bit. Sealed pages refuse
        // writeDatum for any non-null iota, so the seal MUST be keyed per page.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(5.0));

        assertFalse(ItemSpellbook.isSealed(stack), "fresh page not sealed");
        ItemSpellbook.setSealed(stack, true);
        assertTrue(ItemSpellbook.isSealed(stack), "seal persists");

        // Second page has its own seal bit, independent of page 1.
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        assertFalse(ItemSpellbook.isSealed(stack), "page 2 seal is independent");

        // Unsealing clears.
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        ItemSpellbook.setSealed(stack, false);
        assertFalse(ItemSpellbook.isSealed(stack), "unseal clears");
    }

    @Test
    public void spellbookSealedRefusesWrite() {
        // Contract: a sealed page refuses non-null writeDatum (but null to delete still works —
        // the writeable() check is the caller-facing guard, not the enforcement point).
        // Here we verify the non-null path is blocked.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(7.0));
        ItemSpellbook.setSealed(stack, true);

        book.writeDatum(stack, new DoubleIota(999.0));

        var tag = book.readIotaTag(stack);
        assertNotNull(tag);
        assertEquals(7.0, ((DoubleIota) IotaType.deserialize(tag, null)).getDouble(), 0.0,
            "sealed page retains original iota");
        assertFalse(book.writeable(stack),
            "writeable() returns false so callers gate their writes");
    }

    @Test
    public void spellbookVariantClampedAndSealImmutable() {
        // setVariant clamps to [0, numVariants-1]. Sealed books refuse variant changes.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);

        book.setVariant(stack, 1);
        assertEquals(1, book.getVariant(stack), "variant set");
        book.setVariant(stack, 999);
        assertEquals(book.numVariants() - 1, book.getVariant(stack),
            "out-of-range variant clamped to numVariants-1");
        book.setVariant(stack, -5);
        assertEquals(0, book.getVariant(stack), "negative variant clamped to 0");

        // Seal the book, then try to change variant — must stay.
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(0.0));
        ItemSpellbook.setSealed(stack, true);
        int before = book.getVariant(stack);
        book.setVariant(stack, (before + 1) % book.numVariants());
        assertEquals(before, book.getVariant(stack), "sealed book retains variant");
    }

    @Test
    public void spellbookRotatePageIdxStaysZeroWhenEmpty() {
        // rotatePageIdx is a no-op on an empty book — page stays 0 because getPage(stack, 0)
        // returns 0 on empty. The player can't flip through nothing.
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        int after = ItemSpellbook.rotatePageIdx(stack, true);
        assertEquals(0, after, "empty book stays on page 0");

        int after2 = ItemSpellbook.rotatePageIdx(stack, false);
        assertEquals(0, after2, "scrolling back on empty book stays 0");
    }

    @Test
    public void spellbookRotatePageIdxAdvancesFromOneOnWrittenBook() {
        // Once a book has a page written, rotatePageIdx(true) advances from 1→2. Scrolling back
        // is clamped at 1 — you never reach page 0 once the book has content.
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(1.0));

        assertEquals(2, ItemSpellbook.rotatePageIdx(stack, true), "1 → 2");
        assertEquals(1, ItemSpellbook.rotatePageIdx(stack, false), "2 → 1");
        assertEquals(1, ItemSpellbook.rotatePageIdx(stack, false),
            "1 → 1 (scrolling back below 1 is clamped)");
    }

    @Test
    public void spellbookStackCopyCarriesAllPages() {
        // Multi-page spellbooks are the worst-case for custom_data copying: two pages, each
        // with a different iota, both sealed. All that state must travel through .copy().
        var book = (ItemSpellbook) HexItems.SPELLBOOK;
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(10.0));
        ItemSpellbook.setSealed(stack, true);
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        book.writeDatum(stack, new DoubleIota(20.0));

        ItemStack copy = stack.copy();
        NBTHelper.putInt(copy, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        var page1 = book.readIotaTag(copy);
        assertNotNull(page1);
        assertEquals(10.0, ((DoubleIota) IotaType.deserialize(page1, null)).getDouble(), 0.0);
        assertTrue(ItemSpellbook.isSealed(copy), "seal survived copy");

        NBTHelper.putInt(copy, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        var page2 = book.readIotaTag(copy);
        assertNotNull(page2);
        assertEquals(20.0, ((DoubleIota) IotaType.deserialize(page2, null)).getDouble(), 0.0);
        assertFalse(ItemSpellbook.isSealed(copy),
            "only page 1 was sealed; page 2 on the copy is still unsealed");
    }

    // ---- VariantItem smoke tests on the packaged-hex items --------------------------------

    @Test
    public void variantItemNumVariantsIsConsistent() {
        // All four packaged hex items share ItemFocus.NUM_VARIANTS — that's the contract the
        // recipe book relies on when showing color variants. If ItemFocus changed the constant
        // and forgot to bump the others, the UI would show mismatched variant counts.
        var cypher = HexItems.CYPHER;
        var trinket = HexItems.TRINKET;
        var artifact = HexItems.ARTIFACT;
        var ancient = HexItems.ANCIENT_CYPHER;

        int expected = ((VariantItem) cypher).numVariants();
        assertTrue(expected > 0, "cypher has at least one variant");
        assertEquals(expected, ((VariantItem) trinket).numVariants(), "trinket matches cypher");
        assertEquals(expected, ((VariantItem) artifact).numVariants(), "artifact matches cypher");
        assertEquals(expected, ((VariantItem) ancient).numVariants(),
            "ancient cypher matches cypher");
    }

    @Test
    public void frozenPigmentRoundTripViaPackagedHex() {
        // Defense-in-depth: FrozenPigment serializes via RegistryAccess.EMPTY when persisted into
        // a packaged hex, and must come back identical. This duplicates RitualCircleTest's
        // check at the FrozenPigment level, but drives it specifically through the
        // ItemPackagedHex API to catch any bridging regression in writeHex/getPigment.
        var artifact = (ItemArtifact) HexItems.ARTIFACT;
        var owner = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        var pigmentStack = new ItemStack(HexItems.DEFAULT_PIGMENT);
        pigmentStack.setCount(3);
        var pigment = new FrozenPigment(pigmentStack, owner);
        // Ensure the serializeToNBT contract we depend on hasn't changed out from under us.
        var directTag = pigment.serializeToNBT(RegistryAccess.EMPTY);
        var directBack = FrozenPigment.fromNBT(directTag, RegistryAccess.EMPTY);
        assertEquals(owner, directBack.owner(), "pre-check: direct round-trip works");

        ItemStack stack = new ItemStack(artifact);
        artifact.writeHex(stack, makeProgram(), pigment, 1000L);
        var back = artifact.getPigment(stack);
        assertNotNull(back);
        assertEquals(owner, back.owner(), "owner preserved through item round-trip");
        assertEquals(HexItems.DEFAULT_PIGMENT, back.item().getItem());
        assertEquals(3, back.item().getCount(),
            "stack count preserved through item round-trip");
    }
}
