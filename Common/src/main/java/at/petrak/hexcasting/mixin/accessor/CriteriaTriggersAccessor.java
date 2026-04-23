package at.petrak.hexcasting.mixin.accessor;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CriteriaTriggers.class)
public interface CriteriaTriggersAccessor {
    // 1.21: CriteriaTriggers.register(String, T) — id is explicit, no longer via getId().
    @Invoker("register")
    static <T extends CriterionTrigger<?>> T hex$register(String id, T trigger) {
        throw new UnsupportedOperationException();
    }
}
