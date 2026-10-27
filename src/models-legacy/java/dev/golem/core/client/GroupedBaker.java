package dev.golem.core.client;

import dev.golem.core.render.gem.Geometry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class GroupedBaker {
    private static final int STRIDE = Geometry.FLOATS_PER_VERTEX;
    private GroupedBaker() {}

    static List<BakedQuad> bake(GroupedModel model, ModelState state, Function<String, TextureAtlasSprite> sprites) {
        if (state.isUvLocked()) {
            throw new IllegalArgumentException("Grouped model UV locking requires an authored UV policy: " + model.source());
        }
        var result = new ArrayList<BakedQuad>();
        var matrix = state.getRotation().getMatrix();
        var a = new Vector3f();
        var b = new Vector3f();
        var c = new Vector3f();
        var normal = new Vector3f();
        var edge = new Vector3f();
        for (var skin : model.skins().entrySet()) {
            var sprite = sprites.apply(skin.getKey());
            var vertices = model.vertices(skin.getValue());
            for (var at = 0; at < vertices.length; at += 3 * STRIDE) {
                position(a, vertices, at, model, matrix);
                position(b, vertices, at + STRIDE, model, matrix);
                position(c, vertices, at + 2 * STRIDE, model, matrix);
                normal.set(b).sub(a).cross(edge.set(c).sub(a));
                var face = Direction.getNearest(normal.x, normal.y, normal.z);
                var packed = new int[32];
                vertex(packed, 0, a, vertices, at, sprite, face);
                vertex(packed, 8, b, vertices, at + STRIDE, sprite, face);
                vertex(packed, 16, c, vertices, at + 2 * STRIDE, sprite, face);
                vertex(packed, 24, c, vertices, at + 2 * STRIDE, sprite, face);
                result.add(new BakedQuad(packed, -1, face, sprite, true));
            }
        }
        return result;
    }

    private static void position(Vector3f target, float[] data, int at, GroupedModel model,
                                 Matrix4fc matrix) {
        target.set(data[at] + model.offsetX() - 0.5F, data[at + 1] + model.offsetY() - 0.5F,
                data[at + 2] + model.offsetZ() - 0.5F);
        matrix.transformPosition(target);
        target.add(0.5F, 0.5F, 0.5F);
    }

    private static void vertex(int[] target, int out, Vector3f position, float[] source, int at,
                               TextureAtlasSprite sprite, Direction face) {
        target[out] = Float.floatToRawIntBits(position.x);
        target[out + 1] = Float.floatToRawIntBits(position.y);
        target[out + 2] = Float.floatToRawIntBits(position.z);
        target[out + 3] = -1;
        target[out + 4] = Float.floatToRawIntBits(sprite.getU0() + (sprite.getU1() - sprite.getU0()) * source[at + 3]);
        target[out + 5] = Float.floatToRawIntBits(sprite.getV0() + (sprite.getV1() - sprite.getV0()) * source[at + 4]);
        target[out + 7] = (face.getStepX() * 127 & 255) | (face.getStepY() * 127 & 255) << 8
                | (face.getStepZ() * 127 & 255) << 16;
    }
}
