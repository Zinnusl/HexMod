package at.petrak.hexcasting.forge.xplat;

import at.petrak.hexcasting.api.client.ClientCastingStack;
import at.petrak.hexcasting.common.msgs.IMessage;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * TODO(port-1.21): client-side platform bridge. Depends on the excluded cap/ package
 * (for ClientCastingStack lookup) and the stubbed ForgePacketHandler. Both return
 * pass-through defaults; real behaviour lands with the cap rewrite + CustomPacketPayload
 * migration.
 */
public class ForgeClientXplatImpl implements IClientXplatAbstractions {
    @Override
    public void sendPacketToServer(IMessage packet) {
        at.petrak.hexcasting.forge.network.ForgePacketHandler.getNetwork().sendToServer(packet);
    }

    @Override
    public void setRenderLayer(Block block, RenderType type) {
        // 1.21 NeoForge: block render layers are resolved via the block's RenderType
        // registered in ItemBlockRenderTypes; the explicit imperative call has no effect.
    }

    @Override
    public void initPlatformSpecific() {
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
        EntityRendererProvider<T> renderer) {
        EntityRenderers.register(type, renderer);
    }

    @Override
    public void registerItemProperty(Item item, ResourceLocation id, ItemPropertyFunction func) {
        // 1.21: ItemProperties.register only accepts ClampedItemPropertyFunction. Wrap
        // the unclamped input; the clamping is trivially an identity if the source is
        // already well-behaved.
        ItemProperties.register(item, id, (stack, level, holder, holderID) ->
            func.call(stack, level, holder, holderID));
    }

    @Override
    public ClientCastingStack getClientCastingStack(Player player) {
        // TODO(port-1.21): backed by an attached data type (AttachmentType) on the player
        // once hex's cap data is ported to 1.21. Return a fresh empty stack so UI code
        // has something to render.
        return new ClientCastingStack();
    }

    @Override
    public void setFilterSave(AbstractTexture texture, boolean filter, boolean mipmap) {
        // 1.21: AbstractTexture#setBlurMipmap → setFilter(blur, mipmap). There is no
        // "restore" — the previous state isn't stashed for us anymore, so call sites that
        // pair setFilterSave with restoreLastFilter lose the round-trip.
        texture.setFilter(filter, mipmap);
    }

    @Override
    public void restoreLastFilter(AbstractTexture texture) {
        // No-op: the saved-state API was removed on 1.21. Callers that depended on
        // filter state being restored must set it explicitly themselves.
    }

    @Override
    public boolean fabricAdditionalQuenchFrustumCheck(AABB aabb) {
        return true; // NeoForge fixes the offscreen-rendering case with a patch.
    }
}
