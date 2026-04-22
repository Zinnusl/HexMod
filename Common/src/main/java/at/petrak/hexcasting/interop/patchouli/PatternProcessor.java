package at.petrak.hexcasting.interop.patchouli;

import net.minecraft.world.level.Level;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * TODO(port-1.21): IVariableProvider.get signature changed at 1.21 — revisit once the
 * Patchouli 1.21 API surface is settled.
 */
public class PatternProcessor implements IComponentProcessor {
    @Override
    public void setup(Level level, IVariableProvider vars) {
    }

    @Override
    public IVariable process(Level level, String key) {
        return null;
    }
}
