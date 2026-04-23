package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.utils.NBTHelper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NBTHelper is the Kotlin extension layer hex uses everywhere it would have
 * touched {@code stack.getTag()} on 1.20. On 1.21 it bridges through the
 * {@code minecraft:custom_data} DataComponent. Historically this was the single
 * biggest surface of NBT regressions during the port — any break here hits
 * every spellbook, scroll, cypher, staff, abacus, and battery.
 */
public final class NBTHelperCustomDataTest {
    @BeforeAll
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void putAndGetRoundtripScalars() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTHelper.putInt(stack, "count", 42);
        NBTHelper.putLong(stack, "long", 12345678901234L);
        NBTHelper.putString(stack, "name", "wand");
        NBTHelper.putBoolean(stack, "flag", true);

        assertEquals(42, NBTHelper.getInt(stack, "count"), "int survives custom_data write/read");
        assertEquals(12345678901234L, NBTHelper.getLong(stack, "long"), "long survives");
        assertEquals("wand", NBTHelper.getString(stack, "name"), "string survives");
        assertTrue(NBTHelper.getBoolean(stack, "flag"), "boolean survives");
    }

    @Test
    public void missingKeyReturnsDefault() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertEquals(0, NBTHelper.getInt(stack, "nope"), "missing int returns 0");
        // NBTHelper.getString returns null on a missing key — distinguishes absent
        // from empty so downstream code can branch.
        assertNull(NBTHelper.getString(stack, "nope"), "missing string returns null");
        assertFalse(NBTHelper.getBoolean(stack, "nope"), "missing bool returns false");
    }

    @Test
    public void removeActuallyClearsComponent() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTHelper.putInt(stack, "count", 1);
        assertTrue(NBTHelper.hasInt(stack, "count"), "key present after put");
        NBTHelper.remove(stack, "count");
        assertFalse(NBTHelper.hasInt(stack, "count"), "key absent after remove");
    }

    @Test
    public void independentKeysDontClobber() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTHelper.putInt(stack, "a", 1);
        NBTHelper.putInt(stack, "b", 2);
        NBTHelper.putInt(stack, "c", 3);
        assertEquals(1, NBTHelper.getInt(stack, "a"));
        assertEquals(2, NBTHelper.getInt(stack, "b"));
        assertEquals(3, NBTHelper.getInt(stack, "c"));
        // Removing one leaves the others intact — guards against CustomData.update
        // replacing the whole blob instead of updating a field.
        NBTHelper.remove(stack, "b");
        assertEquals(1, NBTHelper.getInt(stack, "a"));
        assertFalse(NBTHelper.hasInt(stack, "b"));
        assertEquals(3, NBTHelper.getInt(stack, "c"));
    }

    @Test
    public void doubleAndFloatPreservePrecision() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTHelper.putDouble(stack, "pi", Math.PI);
        NBTHelper.putFloat(stack, "e", (float) Math.E);
        assertEquals(Math.PI, NBTHelper.getDouble(stack, "pi"), 0.0);
        assertEquals((float) Math.E, NBTHelper.getFloat(stack, "e"), 0.0f);
    }

    @Test
    public void stackCopyCarriesCustomData() {
        // ItemStack.copy must bring the custom_data component with it; hex relies on
        // this when the vanilla crafting-table places an output with carried NBT.
        ItemStack stack = new ItemStack(Items.STICK);
        NBTHelper.putString(stack, "tag", "hello");
        ItemStack copy = stack.copy();
        assertEquals("hello", NBTHelper.getString(copy, "tag"), "custom_data follows copy()");
        // Mutating the copy must not leak back — components are immutable, update
        // returns a new component, stack references the new copy.
        NBTHelper.putString(copy, "tag", "world");
        assertEquals("hello", NBTHelper.getString(stack, "tag"), "original not mutated");
        assertEquals("world", NBTHelper.getString(copy, "tag"), "copy is mutated");
    }
}
