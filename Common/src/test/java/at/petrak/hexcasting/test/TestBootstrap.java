package at.petrak.hexcasting.test;

import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import at.petrak.hexcasting.common.lib.hex.HexContinuationTypes;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared bootstrap for tests that touch {@link HexItems}/{@link HexBlocks} indirectly — e.g.
 * anything that references {@code HexActions.CRAFT$CYPHER} drags in the full item/block graph.
 * <p>
 * Normal {@link Bootstrap#bootStrap()} freezes {@code BuiltInRegistries.ITEM}/{@code BLOCK},
 * which kills {@link MappedRegistry#createIntrusiveHolder} for any hex item/block constructed
 * after the freeze. In a live server these holders get drained by the {@code RegisterEvent}
 * callback on the mod event bus before {@code BuiltInRegistries.freeze()}; we have no mod bus in
 * tests, so we unfreeze the registries with reflection and drain the holders ourselves:
 *
 * <ol>
 *   <li>{@link Bootstrap#bootStrap()} first — we need vanilla {@code SoundEvents},
 *       {@code BlockStateProperties}, etc. before {@link HexBlocks}/{@link HexItems} can load.</li>
 *   <li>Reflection-reset {@code frozen}/{@code unregisteredIntrusiveHolders} on the ITEM and BLOCK
 *       registries — Bootstrap freezes them, but MC's own {@code unfreeze()} leaves
 *       {@code unregisteredIntrusiveHolders} nulled, so the intrusive path stays broken. We null-out
 *       {@code frozen} and replace the intrusive map with a fresh one.</li>
 *   <li>Force {@link HexBlocks}/{@link HexItems} static init — their items/blocks populate their
 *       own {@code LinkedHashMap}s and register intrusive holders against the re-opened
 *       {@code BuiltInRegistries}.</li>
 *   <li>{@link Registry#register} each one so the intrusive holder is bound — otherwise any later
 *       freeze would fail validation.</li>
 * </ol>
 * <p>
 * Idempotent — safe to call from multiple {@code @BeforeAll} hooks.
 */
public final class TestBootstrap {
    private static boolean done = false;

    public static synchronized void init() {
        if (done) return;
        done = true;

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // Reopen the ITEM and BLOCK registries so hex items can construct their intrusive holders.
        reopenRegistry(BuiltInRegistries.ITEM);
        reopenRegistry(BuiltInRegistries.BLOCK);
        // CUSTOM_STAT gets frozen by Bootstrap too — HexStatistics.<clinit> calls
        // Registry.register on it, which throws post-freeze. Don't restore intrusive holders:
        // CUSTOM_STAT's value type (ResourceLocation) has no intrusive-creation path, so
        // register() must take the non-intrusive branch (unregisteredIntrusiveHolders=null).
        reopenRegistry(BuiltInRegistries.CUSTOM_STAT, false);
        // BLOCK_ENTITY_TYPE: BlockEntityType constructor calls createIntrusiveHolder on this
        // registry; hex's BlockEntityTypes are constructed at HexBlockEntities.<clinit>.
        reopenRegistry(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        // PARTICLE_TYPE: HexParticles.<clinit> registers a ConjureParticleOptions.Type via the
        // ParticleType constructor, which may also intrusive-hold.
        reopenRegistry(BuiltInRegistries.PARTICLE_TYPE);

        // Order matters: HexItems.SLATE references HexBlocks.SLATE, so HexBlocks must load first.
        forceLoad("at.petrak.hexcasting.common.lib.HexBlocks");
        forceLoad("at.petrak.hexcasting.common.lib.HexItems");

        HexBlocks.registerBlocks((block, loc) ->
            Registry.register(BuiltInRegistries.BLOCK, loc, block));
        HexItems.registerItems((item, loc) ->
            Registry.register(BuiltInRegistries.ITEM, loc, item));
        HexBlocks.registerBlockItems((item, loc) ->
            Registry.register(BuiltInRegistries.ITEM, loc, item));

        // Populate hex's own registries from the stub — downstream iota/action lookups expect
        // these to be non-empty. Tests that walk actions/iotas without the registry layer can
        // still call the static register() methods directly.
        //
        // Quirk: on 1.21 NeoForge, {@code Registry.register} on a user-created MappedRegistry
        // populates byLocation/byValue/byKey but leaves {@code Holder.Reference#value} null. The
        // Holder path that live servers use to bind values runs in NeoForge's RegisterEvent
        // dispatch, which we don't have in tests. Our workaround: after every register, reflect
        // into the Holder.Reference and set the value field manually. Confirmed via
        // {@link MinimalRegistryTest#canBindValueManuallyViaReflection()} that this is the only
        // step missing — without it {@code registry.get(loc)} throws "unbound value".
        HexIotaTypes.registerTypes((type, loc) -> {
            Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), loc, type);
            forceBindValue(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), loc, type);
        });
        HexActions.register((entry, loc) -> {
            Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), loc, entry);
            forceBindValue(IXplatAbstractions.INSTANCE.getActionRegistry(), loc, entry);
        });
        HexContinuationTypes.registerContinuations((ctype, loc) -> {
            Registry.register(IXplatAbstractions.INSTANCE.getContinuationTypeRegistry(), loc, ctype);
            forceBindValue(IXplatAbstractions.INSTANCE.getContinuationTypeRegistry(), loc, ctype);
        });
    }

    private static <T> void forceBindValue(Registry<T> registry, ResourceLocation loc, T value) {
        try {
            Field byLocation = MappedRegistry.class.getDeclaredField("byLocation");
            byLocation.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (Map<ResourceLocation, Holder.Reference<T>>) byLocation.get(registry);
            var holder = map.get(loc);
            if (holder == null) return;
            Field valueField = Holder.Reference.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(holder, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Couldn't force-bind " + loc + " on " + registry.key(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void reopenRegistry(Registry<?> registry) {
        reopenRegistry(registry, true);
    }

    /**
     * Unfreeze a built-in registry. {@code restoreIntrusiveHolders} controls whether
     * {@code unregisteredIntrusiveHolders} is reset to an empty map — needed for ITEM/BLOCK
     * where hex's items build intrusive holders, but breaks non-intrusive registries like
     * CUSTOM_STAT (whose value type has no intrusive-creation path, so register expects null
     * there to take the non-intrusive branch).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void reopenRegistry(Registry<?> registry, boolean restoreIntrusiveHolders) {
        if (!(registry instanceof MappedRegistry mapped)) {
            throw new IllegalStateException("Expected MappedRegistry, got " + registry.getClass());
        }
        try {
            Field frozen = MappedRegistry.class.getDeclaredField("frozen");
            frozen.setAccessible(true);
            frozen.setBoolean(mapped, false);

            if (restoreIntrusiveHolders) {
                // unregisteredIntrusiveHolders gets nulled on freeze. Restore it so
                // createIntrusiveHolder stops throwing.
                Field intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
                intrusive.setAccessible(true);
                intrusive.set(mapped, new HashMap<>());
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Couldn't reopen registry " + registry.key(), e);
        }
    }

    private static void forceLoad(String fqn) {
        try {
            Class.forName(fqn, true, TestBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't load " + fqn + " for test bootstrap", e);
        }
    }

    private TestBootstrap() {}
}
