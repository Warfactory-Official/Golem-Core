package dev.golem.core.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.font.TextRenderable;
*///?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
//?}

/**
 * Geometry drawn straight away, outside vanilla's submit pass: vanilla's shared buffer source where it has one,
 * Core's own batches on 26.2, which has none. {@link #endBatch} draws each type's batch in first-use order.
 */
public final class ImmediateBuffers {

    //? if >=26.2 {
    /*private final Map<RenderType, Batch> batches = new LinkedHashMap<>();

    private record Batch(ByteBufferBuilder bytes, BufferBuilder builder) {}
    *///?} else {
    private final MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
    //?}

    public VertexConsumer getBuffer(RenderType type) {
        //? if >=26.2 {
        /*return batches.computeIfAbsent(type, t -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
            return new Batch(bytes, new BufferBuilder(bytes, t.primitiveTopology(), t.format()));
        }).builder();
        *///?} else {
        return source.getBuffer(type);
        //?}
    }

    public void text(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f pose,
                     Font.DisplayMode mode, int backgroundColor, int light) {
        //? if >=26.2 {
        /*font.prepareText(text, x, y, color, shadow, backgroundColor).visit(new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(TextRenderable renderable) {
                renderable.render(pose, getBuffer(renderable.renderType(mode)), light, false);
            }
        });
        *///?} else {
        font.drawInBatch(text, x, y, color, shadow, pose, source, mode, backgroundColor, light);
        //?}
    }

    public void endBatch() {
        //? if >=26.2 {
        /*for (var entry : batches.entrySet()) {
            MeshData mesh = entry.getValue().builder().build();
            if (mesh != null) Passes.draw(entry.getKey(), mesh);
            entry.getValue().bytes().close();
        }
        batches.clear();
        *///?} else {
        source.endBatch();
        //?}
    }
}
