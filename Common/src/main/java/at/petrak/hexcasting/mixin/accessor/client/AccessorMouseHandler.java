package at.petrak.hexcasting.mixin.accessor.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21: MouseHandler.accumulatedScroll was split into accumulatedScrollX / accumulatedScrollY.
 * Hex only tracks vertical scroll (for the casting GUI), so we expose accumulatedScrollY
 * but keep the legacy method name for call-site compatibility.
 */
@Mixin(MouseHandler.class)
public interface AccessorMouseHandler {
    @Accessor("accumulatedScrollY")
    double hex$getAccumulatedScroll();

    @Accessor("accumulatedScrollY")
    void hex$setAccumulatedScroll(double scroll);
}
