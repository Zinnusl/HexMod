package at.petrak.hexcasting.interop.patchouli;

import com.google.gson.annotations.SerializedName;
import net.minecraft.ChatFormatting;
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
    transient List<Component> tooltip;

    transient int x, y;

    @Override
    public void build(int componentX, int componentY, int pageNum) {
        x = componentX;
        y = componentY;
        tooltip = new ArrayList<>();
    }

    @Override
    public void render(GuiGraphics graphics, IComponentRenderContext context, float pticks, int mouseX, int mouseY) {
        if (context.isAreaHovered(mouseX, mouseY, x, y, width, height)) {
            context.setHoverTooltipComponents(tooltip);
        }
    }

    // 1.21 Patchouli: onVariablesAvailable grew a HolderLookup.Provider arg for registry-aware reads.
    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, net.minecraft.core.HolderLookup.Provider provider) {
        tooltipVar = lookup.apply(tooltipReference);
        // Resolve the tooltip list now that we have both the lookup and a registry provider.
        // Each entry becomes a translated Component; empty/absent vars leave the tooltip empty.
        for (var line : tooltipVar.asListOrSingleton(provider)) {
            String raw = line.asString();
            if (raw == null || raw.isEmpty()) continue;
            tooltip.add(Component.translatable(raw).withStyle(ChatFormatting.GRAY));
        }
    }
}
