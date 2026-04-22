package at.petrak.hexcasting.interop.patchouli;

import net.minecraft.world.level.Level;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * TODO(port-1.21): Patchouli's IVariableProvider.get signature and the Recipe/RecipeHolder
 * boundary shifted at 1.21 — rebuild the brainsweep recipe display against the new API.
 * Stubbed to no-op so book pages that reference it don't crash compilation.
 */
public class BrainsweepProcessor implements IComponentProcessor {
    @Override
    public void setup(Level level, IVariableProvider vars) {
    }

    @Override
    public IVariable process(Level level, String key) {
        return null;
    }
}
