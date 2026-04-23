package at.petrak.hexcasting.forge.cap;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.player.AltioraAbility;
import at.petrak.hexcasting.api.player.FlightAbility;
import at.petrak.hexcasting.api.player.Sentinel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Player-scoped attachments that replace the 1.20.1 Capability system. Each entry is an
 * {@link AttachmentType} stored on the player entity and serialized to the world save.
 * Retrieval/mutation happens through {@code player.getData(HexAttachments.X)} and
 * {@code player.setData(HexAttachments.X, value)}.
 */
public final class HexAttachments {
    private HexAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HexAPI.MOD_ID);

    // --- FrozenPigment ------------------------------------------------------

    public static final Codec<FrozenPigment> PIGMENT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack", ItemStack.EMPTY).forGetter(FrozenPigment::item),
        UUIDUtil.CODEC.fieldOf("owner").forGetter(FrozenPigment::owner)
    ).apply(inst, FrozenPigment::new));

    public static final Supplier<AttachmentType<FrozenPigment>> PIGMENT = ATTACHMENTS.register(
        "pigment",
        () -> AttachmentType.builder(() -> FrozenPigment.DEFAULT.get())
            .serialize(PIGMENT_CODEC)
            .copyOnDeath()
            .build()
    );

    // --- AltioraAbility -----------------------------------------------------

    public static final Codec<AltioraAbility> ALTIORA_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.fieldOf("grace").forGetter(AltioraAbility::gracePeriod)
    ).apply(inst, AltioraAbility::new));

    /** Stored as Optional<AltioraAbility>; null == no altiora active. */
    public static final Supplier<AttachmentType<java.util.Optional<AltioraAbility>>> ALTIORA = ATTACHMENTS.register(
        "altiora",
        () -> AttachmentType.<java.util.Optional<AltioraAbility>>builder(java.util.Optional::empty)
            .serialize(ALTIORA_CODEC.optionalFieldOf("v").codec())
            .copyOnDeath()
            .build()
    );

    // --- Sentinel -----------------------------------------------------------

    private static final Codec<Vec3> VEC3_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.DOUBLE.fieldOf("x").forGetter(Vec3::x),
        Codec.DOUBLE.fieldOf("y").forGetter(Vec3::y),
        Codec.DOUBLE.fieldOf("z").forGetter(Vec3::z)
    ).apply(inst, Vec3::new));

    public static final Codec<Sentinel> SENTINEL_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.BOOL.fieldOf("extends_range").forGetter(Sentinel::extendsRange),
        VEC3_CODEC.fieldOf("position").forGetter(Sentinel::position),
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Sentinel::dimension)
    ).apply(inst, Sentinel::new));

    public static final Supplier<AttachmentType<java.util.Optional<Sentinel>>> SENTINEL = ATTACHMENTS.register(
        "sentinel",
        () -> AttachmentType.<java.util.Optional<Sentinel>>builder(java.util.Optional::empty)
            .serialize(SENTINEL_CODEC.optionalFieldOf("v").codec())
            .copyOnDeath()
            .build()
    );

    // --- FlightAbility ------------------------------------------------------

    public static final Codec<FlightAbility> FLIGHT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.fieldOf("time").forGetter(FlightAbility::timeLeft),
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(FlightAbility::dimension),
        VEC3_CODEC.fieldOf("origin").forGetter(FlightAbility::origin),
        Codec.DOUBLE.fieldOf("radius").forGetter(FlightAbility::radius)
    ).apply(inst, FlightAbility::new));

    public static final Supplier<AttachmentType<java.util.Optional<FlightAbility>>> FLIGHT = ATTACHMENTS.register(
        "flight",
        () -> AttachmentType.<java.util.Optional<FlightAbility>>builder(java.util.Optional::empty)
            .serialize(FLIGHT_CODEC.optionalFieldOf("v").codec())
            .build()
    );

    // --- Brainswept mob flag ------------------------------------------------

    public static final Supplier<AttachmentType<Boolean>> BRAINSWEPT = ATTACHMENTS.register(
        "brainswept",
        () -> AttachmentType.builder(() -> Boolean.FALSE)
            .serialize(Codec.BOOL)
            .copyOnDeath()
            .build()
    );

    // --- Staffcast image (stored as an opaque CastingImage NBT blob) --------

    public static final Supplier<AttachmentType<net.minecraft.nbt.CompoundTag>> STAFFCAST_IMAGE = ATTACHMENTS.register(
        "staffcast_image",
        () -> AttachmentType.<net.minecraft.nbt.CompoundTag>builder(net.minecraft.nbt.CompoundTag::new)
            .serialize(net.minecraft.nbt.CompoundTag.CODEC)
            .build()
    );

    // --- Patterns saved in the spellcasting UI ------------------------------

    public static final Codec<at.petrak.hexcasting.api.casting.eval.ResolvedPattern> RESOLVED_PATTERN_CODEC =
        net.minecraft.nbt.CompoundTag.CODEC.xmap(
            at.petrak.hexcasting.api.casting.eval.ResolvedPattern::fromNBT,
            at.petrak.hexcasting.api.casting.eval.ResolvedPattern::serializeToNBT
        );

    public static final Supplier<AttachmentType<java.util.List<at.petrak.hexcasting.api.casting.eval.ResolvedPattern>>> PATTERNS = ATTACHMENTS.register(
        "patterns",
        () -> AttachmentType.<java.util.List<at.petrak.hexcasting.api.casting.eval.ResolvedPattern>>builder(
                (Supplier<java.util.List<at.petrak.hexcasting.api.casting.eval.ResolvedPattern>>) java.util.ArrayList::new)
            .serialize(RESOLVED_PATTERN_CODEC.listOf())
            .build()
    );
}
