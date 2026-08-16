package dev.golem.core.render;

//? if forge {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

/^*
 * Asks a block entity's registered renderer whether it draws outside its own block.
 *
 * <p>Lives in its own class so that {@code CompatBlockEntity} — which a dedicated server loads — never
 * names {@link Minecraft} itself. Same reasoning as {@code GolemClientNetwork}: keep the client-only
 * types behind a class the server has no reason to resolve, rather than relying on lazy method
 * verification to save us.
 ^/
public final class ClientRenderBounds {

    private ClientRenderBounds() {}

    /^*
     * Whether the renderer registered for this block entity opted out of frustum culling via
     * {@code shouldRenderOffScreen}. False when no renderer is registered, or when called before the
     * client is far enough along to have a dispatcher.
     ^/
    public static <T extends BlockEntity> boolean rendersOffScreen(T blockEntity) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        BlockEntityRenderer<T> renderer = client.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        return renderer != null && renderer.shouldRenderOffScreen(blockEntity);
    }
}
*///?}
