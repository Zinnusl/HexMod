package at.petrak.hexcasting.client.render.shader;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * TODO(port-1.21): this originally intercepted entity_cutout_no_cull render types to
 * remap their textures, but it did so by digging into RenderType.CompositeRenderType
 * and RenderStateShard.EmptyTextureStateShard — both private inner classes that are
 * no longer reachable via mixin accessors on 1.21 (Java nest-based access control).
 * For now we pass through unchanged so rendering still works; the texture-swap will
 * need a different implementation (perhaps a bespoke RenderType).
 */
public record FakeBufferSource(MultiBufferSource parent,
                               Function<ResourceLocation, RenderType> mapper) implements MultiBufferSource {

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
        return parent.getBuffer(renderType);
    }
}
