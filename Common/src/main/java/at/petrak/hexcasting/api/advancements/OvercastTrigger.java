package at.petrak.hexcasting.api.advancements;

import at.petrak.hexcasting.api.mod.HexConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class OvercastTrigger extends SimpleCriterionTrigger<OvercastTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, int mediaGenerated) {
        super.trigger(player, inst -> {
            var mediaToHealth = HexConfig.common().mediaToHealthRate();
            var healthUsed = mediaGenerated / mediaToHealth;
            return inst.test(mediaGenerated, healthUsed / player.getMaxHealth(), player.getHealth());
        });
    }

    public record Instance(
        Optional<ContextAwarePredicate> player,
        MinMaxBounds.Ints mediaGenerated,
        MinMaxBounds.Doubles healthUsed,
        MinMaxBounds.Doubles healthLeft
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("media_generated", MinMaxBounds.Ints.ANY).forGetter(Instance::mediaGenerated),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("health_used", MinMaxBounds.Doubles.ANY).forGetter(Instance::healthUsed),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("mojang_i_am_begging_and_crying_please_add_an_entity_health_criterion", MinMaxBounds.Doubles.ANY).forGetter(Instance::healthLeft)
            ).apply(inst, Instance::new)
        );

        public boolean test(int mediaGeneratedIn, double healthUsedIn, float healthLeftIn) {
            return this.mediaGenerated.matches(mediaGeneratedIn)
                && this.healthUsed.matches(healthUsedIn)
                && this.healthLeft.matches(healthLeftIn);
        }
    }
}
