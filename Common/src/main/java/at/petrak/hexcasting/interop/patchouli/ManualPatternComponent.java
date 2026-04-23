package at.petrak.hexcasting.interop.patchouli;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.HolderLookup;
import vazkii.patchouli.api.IVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Provide the pattern(s) manually in a book page's template vars.
 * <p>
 * 1.21 Patchouli quirk: {@link IVariable#asListOrSingleton(HolderLookup.Provider)} grew a
 * provider arg. We cache the lookup + provider in {@link #onVariablesAvailable} and resolve
 * the raw pattern list there instead of in {@link #getPatterns} so we don't have to plumb the
 * provider through an extra arg.
 */
public class ManualPatternComponent extends AbstractPatternComponent {
    @SerializedName("patterns")
    public String patternsRaw;
    @SerializedName("stroke_order")
    public String strokeOrderRaw;

    protected transient boolean strokeOrder;
    protected transient List<HexPattern> resolvedPatterns = List.of();

    @Override
    public List<HexPattern> getPatterns(UnaryOperator<IVariable> lookup) {
        return resolvedPatterns;
    }

    @Override
    public boolean showStrokeOrder() {
        return this.strokeOrder;
    }

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider provider) {
        this.strokeOrder = IVariable.wrap(this.strokeOrderRaw).asBoolean(true);

        var patsRaw = lookup.apply(IVariable.wrap(this.patternsRaw)).asListOrSingleton(provider);
        var out = new ArrayList<HexPattern>();
        for (var ivar : patsRaw) {
            JsonElement json = ivar.unwrap();
            RawPattern raw = new Gson().fromJson(json, RawPattern.class);
            var dir = HexDir.fromString(raw.startdir);
            out.add(HexPattern.fromAngles(raw.signature, dir));
        }
        this.resolvedPatterns = out;

        super.onVariablesAvailable(lookup, provider);
    }
}
