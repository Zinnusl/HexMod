package at.petrak.hexcasting.client.render.shader;

import at.petrak.hexcasting.api.HexAPI;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

// https://github.com/VazkiiMods/Botania/blob/3a43accc2fbc439c9f2f00a698f8f8ad017503db/Common/src/main/java/vazkii/botania/client/core/helper/RenderHelper.java
public final class HexRenderTypes extends RenderType {

    private HexRenderTypes(String string, VertexFormat vertexFormat, VertexFormat.Mode mode, int i, boolean bl,
        boolean bl2, Runnable runnable, Runnable runnable2) {
        super(string, vertexFormat, mode, i, bl, bl2, runnable, runnable2);
        throw new UnsupportedOperationException("Should not be instantiated");
    }

    // TODO(port-1.21): RenderType.create now returns CompositeRenderType which is private
    // in 1.21, so this callsite is no longer reachable from outside the nest. The
    // grayscale effect that this render type backed is disabled until we wire it via a
    // NeoForge client bus event (RegisterNamedRenderTypesEvent) or a mixin.
    private static final Function<ResourceLocation, RenderType> GRAYSCALE_PROVIDER = Util.memoize(texture -> {
        return RenderType.entityTranslucentCull(texture);
    });

    public static RenderType getGrayscaleLayer(ResourceLocation texture) {
        return GRAYSCALE_PROVIDER.apply(texture);
    }

}
