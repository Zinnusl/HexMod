/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package at.petrak.hexcasting.interop.patchouli;

import net.minecraft.world.level.Level;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * TODO(port-1.21): Patchouli's IVariable/IVariableProvider signatures and the
 * Recipe / ShapedRecipe / Ingredient APIs all shifted at 1.21. Stubbed to no-op.
 */
public class MultiCraftingProcessor implements IComponentProcessor {
    @Override
    public void setup(Level level, IVariableProvider vars) {
    }

    @Override
    public IVariable process(Level level, String key) {
        return null;
    }
}
