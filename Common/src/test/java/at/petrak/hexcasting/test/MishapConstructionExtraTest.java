package at.petrak.hexcasting.test;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadBlock;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadBrainsweep;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import at.petrak.hexcasting.api.casting.mishaps.MishapEntityTooFarAway;
import at.petrak.hexcasting.api.casting.mishaps.MishapImmuneEntity;
import at.petrak.hexcasting.api.casting.mishaps.MishapInternalException;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidSpellDatumType;
import at.petrak.hexcasting.api.casting.mishaps.MishapLocationInWrongDimension;
import at.petrak.hexcasting.api.casting.mishaps.MishapNoAkashicRecord;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.api.casting.mishaps.MishapTooManyCloseParens;
import at.petrak.hexcasting.api.casting.mishaps.MishapUnenlightened;
import at.petrak.hexcasting.api.casting.mishaps.MishapUnescapedValue;
import at.petrak.hexcasting.api.casting.mishaps.circle.MishapBoolDirectrixEmptyStack;
import at.petrak.hexcasting.api.casting.mishaps.circle.MishapBoolDirectrixNotBool;
import at.petrak.hexcasting.api.casting.mishaps.circle.MishapNoSpellCircle;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sibling to {@link MishapConstructionTest} that covers the remaining {@code Mishap} subclasses
 * under {@code api.casting.mishaps}. Each mishap is constructed with realistic-fake arguments and,
 * where safely reachable without a live {@code Level}/{@code Entity}, its {@code accentColor} and
 * {@code errorMessageWithName} are invoked. accentColor goes through
 * {@code HexItems.DYE_PIGMENTS} — if a port regression left the dye map unfilled, the NPE would
 * fire on every mishap render.
 * <p>
 * Mishaps requiring a live {@code Mob}/{@code Entity} (so {@code entity.displayName} / damage
 * sources work) are covered by class-load only; the full path is covered by in-game {@code
 * @GameTest}s instead. Those are:
 * <ul>
 *   <li>{@link MishapBadBrainsweep} — takes {@code Mob}, {@code execute} calls
 *       {@code trulyHurt(mob, ...)}</li>
 *   <li>{@link MishapBadEntity} — {@code errorMessage} dereferences {@code entity.displayName}</li>
 *   <li>{@link MishapEntityTooFarAway} — same</li>
 *   <li>{@link MishapImmuneEntity} — same</li>
 * </ul>
 */
public final class MishapConstructionExtraTest {
    @BeforeAll
    public static void bootstrap() {
        TestBootstrap.init();
    }

    private final StubCastingEnv env = new StubCastingEnv();
    private final Mishap.Context ctx = new Mishap.Context(null, null);
    private final Mishap.Context ctxNamed = new Mishap.Context(null, Component.literal("test-action"));

    // ---- Simple constructs that can exercise both accentColor and errorMessage ----

    @Test
    public void badCasterConstructsAndRenders() {
        // No fields — accentColor and errorMessage are both pure.
        var m = new MishapBadCaster();
        assertNotNull(m);
        assertAccentColorWorks(m);
        // errorMessageWithName wraps errorMessage — which for this one is pure `error("bad_caster")`.
        assertNotNull(m.errorMessageWithName(env, ctxNamed),
            "bad_caster message composes with action name");
        // Without a name, errorMessageWithName returns the raw errorMessage.
        assertNotNull(m.errorMessageWithName(env, ctx));
    }

    @Test
    public void badBlockConstructs() {
        // BadBlock.errorMessage calls blockAtPos(ctx, pos) which dereferences ctx.world —
        // not reachable without a live level. Confirm construction + accent only.
        var m = new MishapBadBlock(BlockPos.ZERO, Component.literal("cobblestone"));
        assertNotNull(m);
        assertEquals(BlockPos.ZERO, m.getPos());
        assertAccentColorWorks(m);

        // Factory overload that builds the expected Component from a translation-key stub.
        var m2 = MishapBadBlock.of(new BlockPos(1, 2, 3), "impetus");
        assertNotNull(m2);
        assertAccentColorWorks(m2);
    }

    @Test
    public void badItemConstructs() {
        // BadItem takes a live ItemEntity — we can't construct one without a Level. Class-load only.
        assertNotNull(MishapBadItem.class, "MishapBadItem class is loadable");
        // The Kotlin companion `of(entity, stub)` needs the entity too — not reachable here.
    }

    @Test
    public void badLocationConstructsAndRenders() {
        // MishapBadLocation constructor: (Vec3, String) — the type param has a Kotlin default
        // `= "too_far"` which isn't exposed as a Java overload. Pass explicitly.
        var far = new MishapBadLocation(new Vec3(1000, 64, 1000), "too_far");
        assertNotNull(far);
        assertAccentColorWorks(far);
        // errorMessage only uses Vec3Iota.display(location) — pure, safe to call.
        assertNotNull(far.errorMessageWithName(env, ctx));

        // Non-default `type` routes through a different translation key.
        var wrong = new MishapBadLocation(Vec3.ZERO, "in_block");
        assertNotNull(wrong.errorMessageWithName(env, ctxNamed));
    }

    @Test
    public void badOffhandItemConstructsAndRenders() {
        // The item is @Nullable — the null branch picks the `no_item.offhand` message, non-null
        // picks `bad_item.offhand`. Both must compose without NPE.
        var noItem = new MishapBadOffhandItem(null, Component.literal("amethyst"));
        assertNotNull(noItem);
        assertAccentColorWorks(noItem);
        assertNotNull(noItem.errorMessageWithName(env, ctxNamed));

        // Non-null stack — ItemStack.EMPTY triggers the `isEmpty` branch, same as null.
        var empty = new MishapBadOffhandItem(ItemStack.EMPTY, Component.literal("focus"));
        assertNotNull(empty.errorMessageWithName(env, ctx));

        // Non-empty stack — routes through the bad_item branch which touches item.displayName.
        // displayName is a static property of Items.STICK — no level needed.
        var stackedMishap = new MishapBadOffhandItem(new ItemStack(Items.STICK), Component.literal("focus"));
        assertNotNull(stackedMishap.errorMessageWithName(env, ctx));

        // Factory overload — the vararg args fill translation placeholders.
        var factory = MishapBadOffhandItem.of(null, "focus");
        assertNotNull(factory);
    }

    @Test
    public void disallowedSpellConstructsAndRenders() {
        // Kotlin default param `type = "disallowed"` on the secondary constructor isn't exposed
        // as a Java no-arg overload — use the primary (type, actionKey) constructor.
        var generic = new MishapDisallowedSpell("disallowed", null);
        assertNotNull(generic);
        assertAccentColorWorks(generic);
        // Null actionKey → message uses the "<type>_generic" branch.
        assertNotNull(generic.errorMessageWithName(env, ctxNamed));

        // Non-null actionKey → message uses the plain `<type>` branch with a localized action name.
        var specific = new MishapDisallowedSpell(
            "disallowed",
            ResourceLocation.fromNamespaceAndPath("hexcasting", "flight")
        );
        assertNotNull(specific.errorMessageWithName(env, ctx));

        var circle = new MishapDisallowedSpell("disallowed_circle", null);
        assertNotNull(circle.errorMessageWithName(env, ctx));
    }

    @Test
    public void internalExceptionConstructsAndRenders() {
        // Any exception serializes — .toString() is what `error("unknown", exception)` consumes.
        var m = new MishapInternalException(new IllegalStateException("boom"));
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));

        // Exception chain with null message — must not NPE during error composition.
        var chained = new MishapInternalException(new RuntimeException((String) null));
        assertNotNull(chained.errorMessageWithName(env, ctx));
    }

    @Test
    public void invalidSpellDatumTypeConstructsAndRenders() {
        // Takes any object — typical use is a jvm class that leaked into an iota slot.
        var m = new MishapInvalidSpellDatumType("not an iota");
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));

        // Object with an unusual toString still round-trips.
        var o = new Object();
        assertNotNull(new MishapInvalidSpellDatumType(o).errorMessageWithName(env, ctx));
    }

    @Test
    public void locationInWrongDimensionConstructs() {
        // errorMessage calls `ctx.world.dimension()` — needs a real level. Construction-only.
        var m = new MishapLocationInWrongDimension(
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        );
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertEquals("minecraft", m.getProperDimension().getNamespace());
    }

    @Test
    public void noAkashicRecordConstructsAndRenders() {
        // errorMessage uses pos.toShortString() only — pure and safe.
        var m = new MishapNoAkashicRecord(new BlockPos(10, 20, 30));
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));
    }

    @Test
    public void othersNameConstructs() {
        // Takes a Player. errorMessage compares confidant to ctx.castingEntity — StubCastingEnv
        // throws on getCastingEntity(). Construction-only + class load, plus accentColor.
        assertNotNull(MishapOthersName.class, "MishapOthersName class is loadable");
        // The static helpers on the companion object don't need a Player — they walk iotas.
        var nothing = MishapOthersName.getTrueNameFromDatum(new DoubleIota(5.0), null);
        assertNull(nothing, "no player in a double iota");

        var nothingFromArgs = MishapOthersName.getTrueNameFromArgs(
            java.util.List.of(new DoubleIota(1.0), new NullIota()), null);
        assertNull(nothingFromArgs, "no player in a list of non-entity iotas");
    }

    @Test
    public void tooManyCloseParensConstructsAndRenders() {
        var m = new MishapTooManyCloseParens();
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));
    }

    @Test
    public void unenlightenedConstructsAndRendersNull() {
        // This is the rare mishap whose errorMessage() deliberately returns null (the execute()
        // branch is what delivers the visible feedback — dropping items + sound + advancement).
        var m = new MishapUnenlightened();
        assertNotNull(m);
        assertAccentColorWorks(m);
        // When errorMessage returns null, errorMessageWithName returns null too — confirmed
        // rather than asserted non-null.
        assertNull(m.errorMessageWithName(env, ctxNamed),
            "unenlightened has no textual error — null by design");
    }

    @Test
    public void unescapedValueConstructsAndRenders() {
        // Takes any Iota. perpetrator.display() is what the error template consumes.
        var m = new MishapUnescapedValue(new DoubleIota(42.0));
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));

        // NullIota.display() has its own fallback — catches any port regression on the
        // null-display path.
        assertNotNull(new MishapUnescapedValue(new NullIota()).errorMessageWithName(env, ctx));
    }

    // ---- Entity-requiring mishaps — class-load only ----

    @Test
    public void badBrainsweepClassLoadable() {
        // Requires a Mob (execute path calls trulyHurt on it). Full path tested by @GameTest.
        assertNotNull(MishapBadBrainsweep.class, "MishapBadBrainsweep class is loadable");
    }

    @Test
    public void badEntityClassLoadable() {
        // Requires a live Entity — errorMessage dereferences entity.displayName!!.plainCopy().
        // The companion `.of(entity, stub)` path also needs the entity.
        assertNotNull(MishapBadEntity.class, "MishapBadEntity class is loadable");
    }

    @Test
    public void entityTooFarAwayClassLoadable() {
        // errorMessage dereferences entity.displayName!! — needs a live entity.
        assertNotNull(MishapEntityTooFarAway.class, "MishapEntityTooFarAway class is loadable");
    }

    @Test
    public void immuneEntityClassLoadable() {
        // errorMessage dereferences entity.displayName!! — needs a live entity.
        assertNotNull(MishapImmuneEntity.class, "MishapImmuneEntity class is loadable");
    }

    // ---- circle/ sub-package ----

    @Test
    public void boolDirectrixEmptyStackConstructsAndRenders() {
        // Pure errorMessage — takes just a BlockPos.
        var m = new MishapBoolDirectrixEmptyStack(new BlockPos(1, 2, 3));
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));
    }

    @Test
    public void boolDirectrixNotBoolConstructsAndRenders() {
        // Two args: perpetrator Iota + pos. perpetrator.display() is pure for DoubleIota.
        Iota perp = new DoubleIota(0.0);
        var m = new MishapBoolDirectrixNotBool(perp, new BlockPos(0, 64, 0));
        assertNotNull(m);
        assertAccentColorWorks(m);
        assertNotNull(m.errorMessageWithName(env, ctxNamed));

        // NullIota also displays without touching env.
        var nullPerp = new MishapBoolDirectrixNotBool(new NullIota(), BlockPos.ZERO);
        assertNotNull(nullPerp.errorMessageWithName(env, ctx));
    }

    @Test
    public void noSpellCircleConstructsAndRenders() {
        var m = new MishapNoSpellCircle();
        assertNotNull(m);
        assertAccentColorWorks(m);
        // errorMessage is a pure `error("no_spell_circle")` — no env dereference.
        assertNotNull(m.errorMessageWithName(env, ctxNamed));
    }

    // ---- Helper ----

    /**
     * accentColor routes through {@code dyeColor(DyeColor.X)} which does a lookup on
     * {@code HexItems.DYE_PIGMENTS} — a NullPointerException here would indicate the dye-pigment
     * map wasn't populated during bootstrap. The specific DyeColor differs per mishap; all we
     * care about here is that the lookup succeeds and yields a non-null FrozenPigment.
     */
    private void assertAccentColorWorks(Mishap m) {
        FrozenPigment color = m.accentColor(env, ctx);
        assertNotNull(color, () -> m.getClass().getSimpleName() + ".accentColor returned null");
        // dyeColor builds FrozenPigment(new ItemStack(HexItems.DYE_PIGMENTS[color]!!), NIL_UUID).
        // If DYE_PIGMENTS wasn't populated at bootstrap, the ItemStack would wrap null and isEmpty
        // would return true — verify the stack is populated.
        assertFalse(color.item().isEmpty(),
            () -> m.getClass().getSimpleName() + ".accentColor wraps an empty ItemStack "
                + "(DYE_PIGMENTS not populated?)");
    }
}
