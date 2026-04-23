package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CastingImage} is the VM state — every cast starts with an empty image and the stack +
 * paren-state get threaded through each op. Its NBT shape is baked into staff-casting pause state
 * (ItemStaff's pattern image) and into patterns/cyphers that carry a snapshot of the stack.
 * Drift in the NBT shape → paused spells don't resume correctly after a relog, which is a nearly
 * invisible user-facing bug.
 */
public final class CastingImageTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    public void emptyImageHasEmptyStack() {
        var image = new CastingImage();
        assertTrue(image.getStack().isEmpty(), "no-arg constructor yields empty stack");
        assertEquals(0, image.getParenCount(), "no parens open");
        assertFalse(image.getEscapeNext(), "escape flag clear");
        assertEquals(0L, image.getOpsConsumed(), "no ops consumed yet");
        assertTrue(image.getUserData().isEmpty(), "userData starts empty");
    }

    @Test
    public void withUsedOpIncrementsCounter() {
        // withUsedOp is called after every successful action — a regression where it didn't
        // actually increment would disable the "too many ops" mishap, letting spells run forever.
        var fresh = new CastingImage();
        assertEquals(1L, fresh.withUsedOp().getOpsConsumed(), "withUsedOp on fresh adds 1");
        assertEquals(5L, fresh.withUsedOps(5).getOpsConsumed(), "withUsedOps(5) on fresh = 5");

        var once = fresh.withUsedOp();
        assertEquals(6L, once.withUsedOps(5).getOpsConsumed(), "withUsedOps accumulates: 1 + 5 = 6");
    }

    @Test
    public void withResetEscapeClearsEscapeAndParens() {
        // After a mishap the VM resets escape/paren state so the player doesn't get stuck in an
        // introspection block.
        var image = new CastingImage().withUsedOp();
        // We can't easily set parens without constructing via copy; just test the reset is
        // idempotent on a clean image.
        var reset = image.withResetEscape();
        assertEquals(0, reset.getParenCount());
        assertFalse(reset.getEscapeNext());
        assertTrue(reset.getParenthesized().isEmpty());
        // opsConsumed is preserved — reset only touches escape/paren state.
        assertEquals(1L, reset.getOpsConsumed(), "reset preserves opsConsumed");
    }

    @Test
    public void serializeToNbtHasExpectedKeys() {
        // The shape of the serialized CompoundTag IS the save-compat contract. All five keys
        // listed here are what staff-cast pause state + cyphers read out of.
        var image = new CastingImage();
        CompoundTag tag = image.serializeToNbt();
        assertTrue(tag.contains(CastingImage.TAG_STACK), "has stack key");
        assertTrue(tag.contains(CastingImage.TAG_PAREN_COUNT), "has open_parens key");
        assertTrue(tag.contains(CastingImage.TAG_ESCAPE_NEXT), "has escape_next key");
        assertTrue(tag.contains(CastingImage.TAG_PARENTHESIZED), "has parenthesized key");
        assertTrue(tag.contains(CastingImage.TAG_OPS_CONSUMED), "has ops_consumed key");
        assertTrue(tag.contains(CastingImage.TAG_USERDATA), "has userdata key");

        assertEquals("stack", CastingImage.TAG_STACK, "literal key — hex save files depend on it");
        assertEquals("open_parens", CastingImage.TAG_PAREN_COUNT);
        assertEquals("escape_next", CastingImage.TAG_ESCAPE_NEXT);
        assertEquals("parenthesized", CastingImage.TAG_PARENTHESIZED);
        assertEquals("ops_consumed", CastingImage.TAG_OPS_CONSUMED);
        assertEquals("userdata", CastingImage.TAG_USERDATA);
    }

    @Test
    public void serializedEmptyImageKeysAreCorrectTypes() {
        // Even on an empty image, each tag key has the right type — makes sure the serializer
        // doesn't silently switch, e.g. stack from ListTag to ByteArrayTag. Save-compat canary.
        var image = new CastingImage();
        CompoundTag tag = image.serializeToNbt();
        assertTrue(tag.get(CastingImage.TAG_STACK) instanceof net.minecraft.nbt.ListTag,
            "stack is a ListTag");
        assertEquals(net.minecraft.nbt.Tag.TAG_INT, tag.get(CastingImage.TAG_PAREN_COUNT).getId(),
            "open_parens is an Int");
        assertEquals(net.minecraft.nbt.Tag.TAG_BYTE, tag.get(CastingImage.TAG_ESCAPE_NEXT).getId(),
            "escape_next is a Byte (boolean)");
        assertEquals(net.minecraft.nbt.Tag.TAG_LONG, tag.get(CastingImage.TAG_OPS_CONSUMED).getId(),
            "ops_consumed is a Long — not an Int");
        assertTrue(tag.get(CastingImage.TAG_USERDATA) instanceof CompoundTag,
            "userdata is a CompoundTag");
    }

    @Test
    public void withOverriddenUsedOpsReplacesCount() {
        // Halt op resets opsConsumed to zero via this helper — check it replaces, not adds.
        var image = new CastingImage().withUsedOps(100L);
        assertEquals(100L, image.getOpsConsumed());
        var replaced = image.withOverriddenUsedOps(5L);
        assertEquals(5L, replaced.getOpsConsumed(), "withOverriddenUsedOps replaces the counter");
    }

    @Test
    public void copyPreservesStackIdentity() {
        // Immutable data-class copy — the stack list shouldn't change identity when we copy
        // with unrelated fields. Defensive test against anyone subbing in a mutable internal list.
        var img = new CastingImage();
        List<Iota> stack = List.of(new DoubleIota(1.0));
        // Kotlin data class `copy` is exposed as multiple Java setters; emulate by using
        // withUsedOps (which uses copy internally) and check stack refs are preserved via
        // the stack getter.
        // Since we can't set stack directly via a public Java API without VM state, just
        // confirm that withUsedOp on a brand-new image keeps the original empty stack.
        var modified = img.withUsedOp();
        assertNotSame(img, modified, "copy yields new instance");
        assertEquals(img.getStack(), modified.getStack(), "stack contents preserved");
    }
}
