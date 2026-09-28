package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.golem.core.GolemCore;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * A sprite drawn the way vanilla's {@code item/generated} draws it: the front face at {@code z = 8.5/16}, the back
 * at {@code 7.5/16}, and a one-pixel wall along every edge between an opaque texel and a clear one or the border,
 * as {@code ItemModelGenerator} builds it, over the unit square of an item's own space.
 *
 * <p>For a custom item renderer that draws on top of its item's sprite (a glow sheet, a coloured glint). Vanilla's
 * own sprite cannot be the surface for that on any version. 26.1 batches item quads in a fixed buffer that is drawn
 * after custom geometry, so an overlay testing against the sprite's depth runs before the sprite exists; below it
 * a custom renderer is only reached when the item's model is a {@code builtin/entity} marker, which has no sprite
 * at all. Drawing the sprite through the same sink, just before the overlay, gives every version the same surface
 * in the same order.
 *
 * <p>The texture is a plain {@code textures/...png} location, drawn unculled and alpha-tested. Its alpha is read
 * once (through {@code ImageIO}, which reads the same on every version) and kept until {@link #clear}; an animated
 * strip contributes its first frame.
 */
public final class ItemSprites {

    private static final Map<Object, boolean[][]> OPAQUE = new HashMap<>();

    private ItemSprites() {}

    /** Forgets every texel mask, so a resource reload re-reads them. */
    public static void clear() {
        OPAQUE.clear();
    }

    /** Draws {@code texture} as a generated item over the unit square of the pose's space. */
    public static void submit(PoseStack pose, SubmitSink sink,
                              //? if >=26.1 {
                              Identifier texture,
                              //?} else {
                              /*ResourceLocation texture,
                              *///?}
                              int light, int overlay) {
        boolean[][] opaque = OPAQUE.computeIfAbsent(texture, key -> read(texture));
        sink.submitCustomGeometry(pose, CompatRenderTypes.entityCutoutNoCull(texture),
                (p, buffer) -> emit(p, buffer, opaque, light, overlay));
    }

    private static boolean[][] read(
            //? if >=26.1 {
            Identifier texture) {
            //?} else {
            /*ResourceLocation texture) {
            *///?}
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture)) {
            BufferedImage image = javax.imageio.ImageIO.read(in);
            int w = image.getWidth();
            int h = Math.min(image.getHeight(), w);
            boolean[][] opaque = new boolean[w][h];
            for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
                opaque[x][y] = (image.getRGB(x, y) >>> 24) != 0;
            }
            return opaque;
        } catch (Exception e) {
            GolemCore.LOG.warn("Could not read item sprite {}: {}", texture, e.toString());
            return new boolean[0][0];
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, boolean[][] opaque, int light, int overlay) {
        int w = opaque.length;
        if (w == 0) return;
        int h = opaque[0].length;
        float front = 8.5F / 16F, back = 7.5F / 16F;
        // the two faces; a coating pass must repeat these corners in this order to meet them depth for depth
        quad(pose, buffer, light, overlay, 0F, 0F, 1F,
                0, 0, front, 0F, 1F, 1, 0, front, 1F, 1F, 1, 1, front, 1F, 0F, 0, 1, front, 0F, 0F);
        quad(pose, buffer, light, overlay, 0F, 0F, -1F,
                0, 1, back, 0F, 0F, 1, 1, back, 1F, 0F, 1, 0, back, 1F, 1F, 0, 0, back, 0F, 1F);
        // the walls: texel (x, y) counts y down from the top, model y runs up
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!opaque[x][y]) continue;
                float x0 = (float) x / w, x1 = (float) (x + 1) / w;
                float y1 = 1F - (float) y / h, y0 = 1F - (float) (y + 1) / h;
                float u0 = (float) x / w, u1 = (float) (x + 1) / w;
                float v0 = (float) y / h, v1 = (float) (y + 1) / h;
                if (x == 0 || !opaque[x - 1][y]) quad(pose, buffer, light, overlay, -1F, 0F, 0F,
                        x0, y0, back, u0, v1, x0, y0, front, u1, v1, x0, y1, front, u1, v0, x0, y1, back, u0, v0);
                if (x == w - 1 || !opaque[x + 1][y]) quad(pose, buffer, light, overlay, 1F, 0F, 0F,
                        x1, y0, front, u0, v1, x1, y0, back, u1, v1, x1, y1, back, u1, v0, x1, y1, front, u0, v0);
                if (y == 0 || !opaque[x][y - 1]) quad(pose, buffer, light, overlay, 0F, 1F, 0F,
                        x0, y1, front, u0, v0, x1, y1, front, u1, v0, x1, y1, back, u1, v1, x0, y1, back, u0, v1);
                if (y == h - 1 || !opaque[x][y + 1]) quad(pose, buffer, light, overlay, 0F, -1F, 0F,
                        x0, y0, back, u0, v0, x1, y0, back, u1, v0, x1, y0, front, u1, v1, x0, y0, front, u0, v1);
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay,
                             float nx, float ny, float nz, float... v) {
        for (int i = 0; i < 4; i++) {
            int o = i * 5;
            Vertexes.vertex(buffer, pose, v[o], v[o + 1], v[o + 2], 0xFFFFFFFF, v[o + 3], v[o + 4],
                    overlay, light, nx, ny, nz);
        }
    }
}
