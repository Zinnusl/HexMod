package at.petrak.hexcasting.interop.patchouli;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import vazkii.patchouli.api.IVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Provide the pattern(s) manually
 */
public class ManualPatternComponent extends AbstractPatternComponent {
    @SerializedName("patterns")
    public String patternsRaw;
    @SerializedName("stroke_order")
    public String strokeOrderRaw;

    protected transient boolean strokeOrder;

    @Override
    public List<HexPattern> getPatterns(UnaryOperator<IVariable> lookup) {
        // TODO(port-1.21): IVariable.asListOrSingleton grew a registry/provider arg.
        this.strokeOrder = lookup.apply(IVariable.wrap(this.strokeOrderRaw)).asBoolean(true);
        return new ArrayList<>();
    }

    @Override
    public boolean showStrokeOrder() {
        return this.strokeOrder;
    }

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, net.minecraft.core.HolderLookup.Provider provider) {
        this.strokeOrder = IVariable.wrap(this.strokeOrderRaw).asBoolean(true);

        super.onVariablesAvailable(lookup, provider);
    }
}
