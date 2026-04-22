package at.petrak.hexcasting.common.particles;

import at.petrak.hexcasting.common.lib.HexParticles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ConjureParticleOptions(int color) implements ParticleOptions {
    public static final MapCodec<ConjureParticleOptions> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            com.mojang.serialization.Codec.INT.fieldOf("color").forGetter(ConjureParticleOptions::color)
        ).apply(inst, ConjureParticleOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConjureParticleOptions> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, ConjureParticleOptions::color,
            ConjureParticleOptions::new
        );

    @Override
    public ParticleType<?> getType() {
        return HexParticles.CONJURE_PARTICLE;
    }

    // 1.21: ParticleType takes codec + streamCodec directly; no Deserializer / fromCommand.
    public static class Type extends ParticleType<ConjureParticleOptions> {
        public Type(boolean overrideLimiter) {
            super(overrideLimiter);
        }

        @Override
        public MapCodec<ConjureParticleOptions> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ConjureParticleOptions> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
