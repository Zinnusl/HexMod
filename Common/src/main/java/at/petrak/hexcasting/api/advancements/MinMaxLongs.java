package at.petrak.hexcasting.api.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Long-typed min/max bound. In 1.21 {@code net.minecraft.advancements.critereon.MinMaxBounds}
 * became a sealed interface tightly coupled to its Int/Double subtypes, so this standalone
 * class mirrors the API hex depends on without inheriting from it.
 */
public final class MinMaxLongs {
    public static final MinMaxLongs ANY = new MinMaxLongs(Optional.empty(), Optional.empty());

    public static final Codec<MinMaxLongs> CODEC = RecordCodecBuilder.create(inst ->
        inst.group(
            Codec.LONG.optionalFieldOf("min").forGetter(MinMaxLongs::minOpt),
            Codec.LONG.optionalFieldOf("max").forGetter(MinMaxLongs::maxOpt)
        ).apply(inst, MinMaxLongs::new)
    );

    private final Optional<Long> min;
    private final Optional<Long> max;
    private final Optional<Long> minSq;
    private final Optional<Long> maxSq;

    private MinMaxLongs(Optional<Long> min, Optional<Long> max) {
        this.min = min;
        this.max = max;
        this.minSq = min.map(l -> l * l);
        this.maxSq = max.map(l -> l * l);
    }

    public Optional<Long> minOpt() {
        return this.min;
    }

    public Optional<Long> maxOpt() {
        return this.max;
    }

    public boolean isAny() {
        return this.min.isEmpty() && this.max.isEmpty();
    }

    public static MinMaxLongs exactly(long l) {
        return new MinMaxLongs(Optional.of(l), Optional.of(l));
    }

    public static MinMaxLongs between(long min, long max) {
        return new MinMaxLongs(Optional.of(min), Optional.of(max));
    }

    public static MinMaxLongs atLeast(long min) {
        return new MinMaxLongs(Optional.of(min), Optional.empty());
    }

    public static MinMaxLongs atMost(long max) {
        return new MinMaxLongs(Optional.empty(), Optional.of(max));
    }

    public boolean matches(long l) {
        if (this.min.isPresent() && this.min.get() > l) {
            return false;
        }
        return this.max.isEmpty() || this.max.get() >= l;
    }

    public boolean matchesSqr(long l) {
        if (this.minSq.isPresent() && this.minSq.get() > l) {
            return false;
        }
        return this.maxSq.isEmpty() || this.maxSq.get() >= l;
    }
}
