package at.petrak.hexcasting.interop.patchouli;

import com.google.gson.annotations.SerializedName;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class CustomComponentTooltip implements ICustomComponent {
    int width, height;

    @SerializedName("tooltip")
    IVariable tooltipReference;

    transient IVariable tooltipVar;
    transient List<Component> tooltip = new ArrayList<>();

    transient int x, y;

    @Override
    public void build(int componentX, int componentY, int pageNum) {
        x = componentX;
        y = componentY;
    }

    @Override
    public void render(GuiGraphics graphics, IComponentRenderContext context, float pticks, int mouseX, int mouseY) {
        if (context.isAreaHovered(mouseX, mouseY, x, y, width, height)) {
            context.setHoverTooltipComponents(tooltip);
        }
    }

    // 1.21 Patchouli: onVariablesAvailable grew a HolderLookup.Provider arg for registry-aware
    // reads. The tooltip list is JSON Components on the page; deserialize each through
    // IVariable.as(Component.class) to match the pre-port behavior. Entries are NOT plain
    // strings — asString would throw UnsupportedOperationException on JsonObject.
    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, net.minecraft.core.HolderLookup.Provider provider) {
        this.tooltipVar = lookup.apply(tooltipReference);
        this.tooltip = new ArrayList<>();
        for (IVariable s : this.tooltipVar.asListOrSingleton(provider)) {
            this.tooltip.add(s.as(Component.class));
        }
    }
}
