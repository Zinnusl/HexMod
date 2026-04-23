package at.petrak.hexcasting.interop.patchouli;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * Resolves the {@code #translation_key} placeholder in the {@code thehexbook/pattern} page
 * template. On 1.21 Patchouli {@code IVariableProvider.get} takes a
 * {@link net.minecraft.core.HolderLookup.Provider} — pull it from the level in {@code setup}.
 * <p>
 * The template sets either {@code header} (explicit override) or {@code op_id} (derive
 * {@code hexcasting.action.<opId>} with an optional {@code hexcasting.action.book.<opId>}
 * override). If the override exists in the lang file, prefer it.
 */
public class PatternProcessor implements IComponentProcessor {
    private String translationKey;

    @Override
    public void setup(Level level, IVariableProvider vars) {
        var lookup = level.registryAccess();
        if (vars.has("header")) {
            translationKey = vars.get("header", lookup).asString();
        } else {
            IVariable key = vars.get("op_id", lookup);
            String opName = key.asString();

            String prefix = "hexcasting.action.";
            boolean hasOverride = I18n.exists(prefix + "book." + opName);
            translationKey = prefix + (hasOverride ? "book." : "") + opName;
        }
    }

    @Override
    public IVariable process(Level level, String key) {
        if (key.equals("translation_key")) {
            return IVariable.wrap(translationKey);
        }
        return null;
    }
}
