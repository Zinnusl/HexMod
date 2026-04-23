package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.storage.ItemAbacus;
import at.petrak.hexcasting.common.items.storage.ItemFocus;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.items.storage.ItemThoughtKnot;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Storage items (focus, spellbook, thought-knot, abacus, scroll) all round-trip user iotas via
 * {@code IotaType.serialize} → stack custom-data → {@code IotaType.deserialize}. On 1.20 this
 * rode on {@code stack.getTag()}; on 1.21 the same path bridges through
 * {@code minecraft:custom_data}. {@link NBTHelperCustomDataTest} already covers the primitive
 * bridge — this test layers on top of it, using each item's public API to confirm iotas survive
 * the full storage round-trip.
 */
public final class ItemNBTRoundtripTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void focusStoresAndReadsIota() {
        ItemStack stack = new ItemStack(HexItems.FOCUS);
        HexItems.FOCUS.writeDatum(stack, new DoubleIota(42.0));
        var tag = HexItems.FOCUS.readIotaTag(stack);
        assertNotNull(tag, "focus should have data after write");
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof DoubleIota, "got " + back.getClass().getSimpleName());
        assertEquals(42.0, ((DoubleIota) back).getDouble(), 0.0);
    }

    @Test
    public void focusSealedIsImmutable() {
        // Sealed foci are the contract players rely on for trinket sharing — once sealed, writes
        // must NOT mutate the stored iota. Regression here would lose player-provided data.
        ItemStack stack = new ItemStack(HexItems.FOCUS);
        HexItems.FOCUS.writeDatum(stack, new DoubleIota(1.0));
        ItemFocus.seal(stack);
        assertTrue(ItemFocus.isSealed(stack), "sealed flag set");

        HexItems.FOCUS.writeDatum(stack, new DoubleIota(999.0));
        var tag = HexItems.FOCUS.readIotaTag(stack);
        assertNotNull(tag);
        var back = IotaType.deserialize(tag, null);
        assertEquals(1.0, ((DoubleIota) back).getDouble(), 0.0,
            "sealed focus must retain original iota — write was a no-op");
    }

    @Test
    public void thoughtKnotWriteableContract() {
        // Thought-knots surface a write-once contract via writeable(): callers must check it
        // before overwriting, otherwise the node loses its captured iota. The IotaHolderItem
        // contract delegates enforcement to the caller.
        ItemStack stack = new ItemStack(HexItems.THOUGHT_KNOT);
        var knot = (ItemThoughtKnot) HexItems.THOUGHT_KNOT;
        assertTrue(knot.writeable(stack), "fresh thought-knot is writeable");

        knot.writeDatum(stack, new DoubleIota(5.0));
        assertFalse(knot.writeable(stack),
            "after first write, writeable() returns false so callers skip overwriting");

        // And the iota survives the round-trip.
        var back = IotaType.deserialize(knot.readIotaTag(stack), null);
        assertEquals(5.0, ((DoubleIota) back).getDouble(), 0.0);
    }

    @Test
    public void abacusStoresDoubleValue() {
        // ItemAbacus is how the default "scratch number" item works — it stores a raw double,
        // not a serialized iota. Read-through is via readIotaTag which wraps the double.
        ItemStack stack = new ItemStack(HexItems.ABACUS);
        NBTHelper.putDouble(stack, ItemAbacus.TAG_VALUE, 13.5);
        var tag = ((ItemAbacus) HexItems.ABACUS).readIotaTag(stack);
        assertNotNull(tag, "abacus should yield an iota tag when TAG_VALUE is set");
        var back = IotaType.deserialize(tag, null);
        assertTrue(back instanceof DoubleIota);
        assertEquals(13.5, ((DoubleIota) back).getDouble(), 0.0);
    }

    @Test
    public void stackCopyCarriesFocusData() {
        // The vanilla crafting table copies stacks during recipe output. Hex items must survive
        // that copy — if custom_data doesn't follow the copy, the player loses the iota.
        ItemStack stack = new ItemStack(HexItems.FOCUS);
        HexItems.FOCUS.writeDatum(stack, new DoubleIota(7.25));
        ItemStack copy = stack.copy();
        var backTag = HexItems.FOCUS.readIotaTag(copy);
        assertNotNull(backTag, "copied focus should still have data");
        var back = IotaType.deserialize(backTag, null);
        assertEquals(7.25, ((DoubleIota) back).getDouble(), 0.0);
    }

    @Test
    public void spellbookHasDistinctPages() {
        // Spellbooks index their pages — each page holds a separate iota. Writing to one page
        // must NOT overwrite another. Regression in NBTHelper.putCompound keyed-update would
        // collapse the whole book to the latest write.
        // We drive the selected-page index directly rather than via rotatePageIdx(), which is
        // a no-op on an empty book.
        ItemStack stack = new ItemStack(HexItems.SPELLBOOK);
        var book = (ItemSpellbook) HexItems.SPELLBOOK;

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        book.writeDatum(stack, new DoubleIota(1.0));

        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 2);
        book.writeDatum(stack, new DoubleIota(2.0));

        // Selected page must reflect our last set — regression here would hide the
        // page-2 iota.
        assertEquals(2, ItemSpellbook.getPage(stack, 0), "current page is 2");
        var page2Tag = book.readIotaTag(stack);
        assertNotNull(page2Tag);
        assertEquals(2.0, ((DoubleIota) IotaType.deserialize(page2Tag, null)).getDouble(), 0.0);

        // Flip back to page 1 — the iota we wrote there must still be there.
        NBTHelper.putInt(stack, ItemSpellbook.TAG_SELECTED_PAGE, 1);
        var page1Tag = book.readIotaTag(stack);
        assertNotNull(page1Tag, "page 1 still has data");
        assertEquals(1.0, ((DoubleIota) IotaType.deserialize(page1Tag, null)).getDouble(), 0.0,
            "page 1 iota must be unchanged after writing to page 2");
    }
}
