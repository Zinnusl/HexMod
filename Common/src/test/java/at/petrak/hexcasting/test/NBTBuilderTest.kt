package at.petrak.hexcasting.test

import at.petrak.hexcasting.api.utils.NBTBuilder
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * [NBTBuilder] (file is `NBTDsl.kt`) is the Kotlin DSL hex uses everywhere it builds a
 * [CompoundTag] — serialization paths on [at.petrak.hexcasting.api.casting.eval.vm.CastingImage],
 * packets, iota types, resolved patterns. The `%=` operator and the `compound/list` builders are
 * the workhorses; silent drift in their semantics (e.g. `%= 42` producing a `LongTag` instead of
 * an `IntTag`) would silently corrupt every file that round-trips through these builders.
 *
 * Kotlin-side so the `remAssign` operator and receiver-scope builders are actually invoked rather
 * than handwritten as `tag.putInt(...)` — the tests exercise the DSL surface, not the underlying
 * CompoundTag API.
 */
class NBTBuilderTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = TestBootstrap.init()
    }

    @Test
    fun intAssignmentProducesIntTag() {
        // `%= 42` picks the Int overload of remAssign. The resulting tag must be an IntTag
        // with TAG_Int id (3), not TAG_Long (4) — getInt reading a LongTag returns 0 silently.
        val tag = NBTBuilder { "foo" %= 42 }
        assertTrue(tag.contains("foo"), "key 'foo' present")
        assertEquals(Tag.TAG_INT.toInt(), tag.getTagType("foo").toInt(),
            "stored tag must be TAG_Int so getInt works")
        assertEquals(42, tag.getInt("foo"), "int value round-trips")
    }

    @Test
    fun stringAssignmentProducesStringTag() {
        val tag = NBTBuilder { "s" %= "hello" }
        assertTrue(tag.contains("s"), "key 's' present")
        assertEquals(Tag.TAG_STRING.toInt(), tag.getTagType("s").toInt(),
            "stored tag must be TAG_String")
        assertEquals("hello", tag.getString("s"), "string value round-trips")
    }

    @Test
    fun nestedListProducesListTag() {
        // list(...) inside a compound produces a ListTag; `%=` routes it through the Tag overload.
        val tag = NBTBuilder {
            "l" %= list(NBTBuilder.int(1), NBTBuilder.int(2), NBTBuilder.int(3))
        }
        assertTrue(tag.contains("l"), "key 'l' present")
        assertEquals(Tag.TAG_LIST.toInt(), tag.getTagType("l").toInt(),
            "stored tag must be TAG_List")
        val list: ListTag = tag.getList("l", Tag.TAG_INT.toInt())
        assertEquals(3, list.size, "list has 3 elements")
        assertEquals(1, list.getInt(0))
        assertEquals(2, list.getInt(1))
        assertEquals(3, list.getInt(2))
    }

    @Test
    fun longAssignmentProducesLongTag() {
        // `%= 12345L` picks the Long overload — a Long literal must not collapse to Int.
        val tag = NBTBuilder { "big" %= 12345678901234L }
        assertEquals(Tag.TAG_LONG.toInt(), tag.getTagType("big").toInt(),
            "stored tag must be TAG_Long")
        assertEquals(12345678901234L, tag.getLong("big"), "long value round-trips")
    }

    @Test
    fun doubleAndFloatAssignmentProduceDistinctTags() {
        // Double and Float overloads of remAssign must route to DoubleTag / FloatTag respectively.
        // Collapsing either to the other would silently halve or double precision depending on
        // direction — an obvious regression but one that's easy to reintroduce.
        val tag = NBTBuilder {
            "d" %= Math.PI
            "f" %= 2.5f
        }
        assertEquals(Tag.TAG_DOUBLE.toInt(), tag.getTagType("d").toInt(),
            "Math.PI stored as DoubleTag")
        assertEquals(Tag.TAG_FLOAT.toInt(), tag.getTagType("f").toInt(),
            "2.5f stored as FloatTag")
        assertEquals(Math.PI, tag.getDouble("d"), 0.0)
        assertEquals(2.5f, tag.getFloat("f"), 0.0f)
    }

    @Test
    fun booleanAssignmentProducesByteTag() {
        // Minecraft NBT has no bool type; booleans serialize as TAG_Byte 0/1. The DSL must match.
        val tag = NBTBuilder {
            "t" %= true
            "f" %= false
        }
        assertEquals(Tag.TAG_BYTE.toInt(), tag.getTagType("t").toInt(), "bool -> TAG_Byte")
        assertEquals(Tag.TAG_BYTE.toInt(), tag.getTagType("f").toInt(), "bool -> TAG_Byte")
        assertTrue(tag.getBoolean("t"), "true round-trips")
        assertFalse(tag.getBoolean("f"), "false round-trips")
    }

    @Test
    fun nestedCompoundProducesCompoundTag() {
        // compound-in-compound is how nested iota serialization works. The inner block runs
        // against a fresh NbtCompoundBuilder — the outer tag must carry the nested CompoundTag.
        val tag = NBTBuilder {
            "outer" %= compound {
                "inner" %= 7
            }
        }
        assertEquals(Tag.TAG_COMPOUND.toInt(), tag.getTagType("outer").toInt(),
            "nested compound stored as TAG_Compound")
        val inner: CompoundTag = tag.getCompound("outer")
        assertEquals(7, inner.getInt("inner"), "inner int survives nesting")
    }

    @Test
    fun useOverloadMutatesExistingTag() {
        // NBTBuilder(tag, block) writes into an existing tag rather than a fresh one — used when
        // a mod wants to additively edit a CompoundTag already owned by some other object.
        val preexisting = CompoundTag()
        preexisting.putString("original", "keep-me")
        val returned = NBTBuilder(preexisting) {
            "added" %= 99
        }
        assertSame(preexisting, returned,
            "NBTBuilder(tag, block) must return the same tag instance, not a copy")
        assertEquals("keep-me", returned.getString("original"),
            "pre-existing key survives the edit")
        assertEquals(99, returned.getInt("added"),
            "new key is written in place")
    }

    @Test
    fun listBuilderUnaryPlusAddsElements() {
        // Inside a list { ... } block the `+tag` operator appends to the builder. This is the
        // direct form used by HexPattern.serializeToNBT for the angle list — wrong behaviour
        // there would scramble pattern directions on every save.
        val tag = NBTBuilder {
            "xs" %= list {
                +NBTBuilder.int(10)
                +NBTBuilder.int(20)
            }
        }
        val list: ListTag = tag.getList("xs", Tag.TAG_INT.toInt())
        assertEquals(2, list.size)
        assertEquals(10, list.getInt(0))
        assertEquals(20, list.getInt(1))
    }

    @Test
    fun topLevelStandaloneConstructors() {
        // NBTBuilder.int / string / long / double are the type-specific entry points callers use
        // outside of a compound { } block (e.g. passing a tag to Registry entries). They must
        // produce the correct primitive tag types.
        assertEquals(Tag.TAG_INT.toInt(), NBTBuilder.int(1).id.toInt())
        assertEquals(Tag.TAG_LONG.toInt(), NBTBuilder.long(1).id.toInt())
        assertEquals(Tag.TAG_DOUBLE.toInt(), NBTBuilder.double(1).id.toInt())
        assertEquals(Tag.TAG_FLOAT.toInt(), NBTBuilder.float(1).id.toInt())
        assertEquals(Tag.TAG_SHORT.toInt(), NBTBuilder.short(1).id.toInt())
        assertEquals(Tag.TAG_BYTE.toInt(), NBTBuilder.byte(1).id.toInt())
        assertEquals(Tag.TAG_STRING.toInt(), NBTBuilder.string("x").id.toInt())
    }
}
