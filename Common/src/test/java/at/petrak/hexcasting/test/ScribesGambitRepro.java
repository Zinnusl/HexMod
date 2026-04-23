package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.common.items.storage.ItemSlate;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproducing the in-game bug: Scribe's Gambit (OpWrite) fails on a blank slate.
 * OpWrite goes through {@code IXplatAbstractions.INSTANCE.findDataHolder(stack)} which wraps
 * an {@link IotaHolderItem} in an AD adapter. The adapter's {@code writeIota(iota, true)}
 * simulation calls {@code item.canWrite(stack, iota)} — for ItemSlate that's
 * {@code datum instanceof PatternIota || datum == null}.
 * <p>
 * The test drives that exact flow outside of a live server.
 */
public final class ScribesGambitRepro {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void blankSlateAcceptsAPatternIota() {
        ItemStack slate = new ItemStack(HexItems.SLATE);
        var slateItem = (ItemSlate) slate.getItem();
        var patternIota = new PatternIota(HexPattern.fromAngles("aawaawaa", HexDir.EAST));

        assertTrue(slateItem.writeable(slate),
            "blank slate should be writeable");
        assertTrue(slateItem.canWrite(slate, patternIota),
            "blank slate must accept a PatternIota — if this fails, Scribe's Gambit breaks");

        // And a full write round-trip.
        slateItem.writeDatum(slate, patternIota);
        var tag = slateItem.readIotaTag(slate);
        assertNotNull(tag, "slate should hold an iota tag after writeDatum");
    }

    @Test
    public void iotaHolderItemCastWorks() {
        // The AD adapter checks `item instanceof IotaHolderItem`. Confirm.
        ItemStack slate = new ItemStack(HexItems.SLATE);
        assertTrue(slate.getItem() instanceof IotaHolderItem,
            "ItemSlate must implement IotaHolderItem so findDataHolder finds it");
    }
}
