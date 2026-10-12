package dev.golem.core.render;

//? if forge {
/*import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/^* Forge 1.20's per-item bridge for a renderer installed once by loader client glue. ^/
public final class ForgeItemRenderers {

    private static IClientItemExtensions extension;

    private ForgeItemRenderers() {}

    public static void install(Renderer renderer) {
        extension = new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer itemRenderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) {
                    // A named class, not an anonymous one: javac derives an anonymous constructor's
                    // parameters from the superclass's, and some generated Forge 1.20.1 jars name them
                    // all alike ("variable o is already defined").
                    final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
                        ItemRenderer(Minecraft minecraft) {
                            super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                        }

                        @Override
                        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                                 MultiBufferSource buffers, int light, int overlay) {
                            renderer.render(stack, context, poseStack, buffers, light, overlay);
                        }
                    }
                    itemRenderer = new ItemRenderer(Minecraft.getInstance());
                }
                return itemRenderer;
            }
        };
    }

    public static void initialize(Consumer<IClientItemExtensions> consumer) {
        if (extension != null) consumer.accept(extension);
    }

    @FunctionalInterface
    public interface Renderer {
        void render(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                    MultiBufferSource buffers, int light, int overlay);
    }
}
*///?}
