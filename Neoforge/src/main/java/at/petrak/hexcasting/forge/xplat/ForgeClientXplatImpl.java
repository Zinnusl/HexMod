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
 * Client-side platform bridge for NeoForge 1.21. Packet sending routes through
 * {@link at.petrak.hexcasting.forge.network.ForgePacketHandler}; entity renderer
 * registration and item-property functions use the 1.21 APIs directly.
 * {@link #getClientCastingStack} still returns a fresh empty stack because hex
 * doesn't yet sync the stack to clients via an AttachmentType (pattern/spiral packets
 * mutate the client-local stack instead).
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

    // Per-player client-side casting stack. Lives only on the client — no save, no sync.
    // WeakHashMap so the stack goes away when the player leaves and is garbage collected.
    private static final java.util.WeakHashMap<Player, ClientCastingStack> CLIENT_STACKS = new java.util.WeakHashMap<>();

    @Override
    public ClientCastingStack getClientCastingStack(Player player) {
        return CLIENT_STACKS.computeIfAbsent(player, p -> new ClientCastingStack());
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
