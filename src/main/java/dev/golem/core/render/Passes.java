package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;
//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import VertexFormat.IndexType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
*///?} else {
import java.util.OptionalInt;
//?}
//? if >=26.3 {
/*import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.StagedVertexBuffer;
*///?}

/**
 * Immediate draws: one spelling of vanilla's vertex upload, render pass and draw calls for every node. 26.3 has no
 * output targets: a Core render type that draws into a layer of its own registers it with {@code layer}.
 */
public final class Passes {

    //? if >=26.2 {
    /*private static final Map<VertexFormat, GpuBuffer> VERTICES = new HashMap<>();
    private static final Map<VertexFormat, GpuBuffer> INDICES = new HashMap<>();
    *///?}
    //? if >=26.3 {
    /*private static final Map<RenderType, Supplier<RenderTarget>> LAYERS = new ConcurrentHashMap<>();
    *///?}

    private Passes() {}

    /** Uploads into the format's shared immediate buffer, reused by the next upload of the same format. */
    public static GpuBuffer upload(VertexFormat format, ByteBuffer data) {
        //? if >=26.2 {
        /*return VERTICES.compute(format, (f, old) -> upload(old, data, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                () -> "Golem immediate vertices for " + f));
        *///?} else {
        return format.uploadImmediateVertexBuffer(data);
        //?}
    }

    //? if >=26.2 {
    /*private static GpuBuffer upload(@Nullable GpuBuffer target, ByteBuffer data, int usage, Supplier<String> label) {
        var device = RenderSystem.getDevice();
        if (target != null && target.size() >= data.remaining()) {
            device.createCommandEncoder().writeToBuffer(target.slice(), data);
            return target;
        }
        if (target != null) target.close();
        return device.createBuffer(label, usage, data);
    }
    *///?}

    /** An RGBA8 colour target, with a depth attachment when {@code depth}. */
    public static TextureTarget target(@Nullable String label, int width, int height, boolean depth) {
        //? if >=26.3 {
        /*return new TextureTarget(label, width, height, GpuFormat.RGBA8_UNORM, depth ? GpuFormat.D32_FLOAT : null);
        *///?} elif >=26.2 {
        /*return new TextureTarget(label, width, height, depth, GpuFormat.RGBA8_UNORM);
        *///?} else {
        return new TextureTarget(label, width, height, depth);
        //?}
    }

    public static boolean hasDepth(RenderTarget target) {
        //? if >=26.3 {
        /*return target.hasDepth();
        *///?} else {
        return target.useDepth;
        //?}
    }

    /** Where a world draw goes now: a picture-in-picture texture while one is being drawn, else the main target. */
    public static GpuTextureView outputColor() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        //? if >=26.3 {
        /*return main.getColorTextureView();
        *///?} else {
        return RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride
                : main.getColorTextureView();
        //?}
    }

    public static @Nullable GpuTextureView outputDepth() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!hasDepth(main)) return null;
        //? if >=26.3 {
        /*return main.getDepthTextureView();
        *///?} else {
        return RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride
                : main.getDepthTextureView();
        //?}
    }

    /** A pass that loads both attachments as they are; {@code depth} may be null. */
    public static RenderPass open(Supplier<String> label, GpuTextureView color, @Nullable GpuTextureView depth) {
        //? if >=26.2 {
        /*return RenderSystem.getDevice().createCommandEncoder().createRenderPass(label, color, Optional.empty(), depth,
                OptionalDouble.empty());
        *///?} else {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(label, color, OptionalInt.empty(), depth,
                OptionalDouble.empty());
        //?}
    }

    public static void pipeline(RenderPass pass, RenderPipeline pipeline) {
        //? if >=26.3 {
        /*pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        *///?} else {
        pass.setPipeline(pipeline);
        //?}
    }

    public static void texture(RenderPass pass, String name, @Nullable GpuTextureView view, @Nullable GpuSampler sampler) {
        //? if >=26.3 {
        /*pass.setUniform(name, view, sampler);
        *///?} else {
        pass.bindTexture(name, view, sampler);
        //?}
    }

    public static void vertices(RenderPass pass, GpuBuffer buffer) {
        //? if >=26.2 {
        /*pass.setVertexBuffer(0, buffer.slice());
        *///?} else {
        pass.setVertexBuffer(0, buffer);
        //?}
    }

    /** Non-indexed, from vertex 0. */
    public static void draw(RenderPass pass, int vertexCount) {
        //? if >=26.2 {
        /*pass.draw(vertexCount, 1, 0, 0);
        *///?} else {
        pass.draw(0, vertexCount);
        //?}
    }

    public static void drawIndexed(RenderPass pass, int firstIndex, int indexCount) {
        //? if >=26.2 {
        /*pass.drawIndexed(indexCount, 1, firstIndex, 0, 0);
        *///?} else {
        pass.drawIndexed(0, firstIndex, indexCount, 1);
        //?}
    }

    /** {@code RenderType.draw(mesh)}, which 26.2 replaced with prepared types; closes {@code mesh}. */
    public static void draw(RenderType type, MeshData mesh) {
        //? if >=26.2 {
        /*try (mesh) {
            GpuBuffer vertices = upload(type.format(), mesh.vertexBuffer());
            GpuBuffer indices;
            IndexType indexType;
            if (mesh.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = sequential.getBuffer(mesh.drawState().indexCount());
                indexType = sequential.type();
            } else {
                indices = INDICES.compute(type.format(), (f, old) -> upload(old, mesh.indexBuffer(),
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, () -> "Golem immediate indices for " + f));
                indexType = mesh.drawState().indexType();
            }
            //? if >=26.3 {
            /^RenderTarget target = layerOf(type);
            var prepared = type.prepare();
            try (RenderPass pass = open(() -> "Golem immediate " + type, target.getColorTextureView(),
                    hasDepth(target) ? target.getDepthTextureView() : null)) {
                RenderSystem.bindDefaultUniforms(pass);
                prepared.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(vertices, indices, indexType, 0, 0,
                        mesh.drawState().indexCount(), mesh.drawState().mode()), pass);
            }
            ^///?} else {
            type.prepare().drawFromBuffer(vertices, indices, indexType, 0, 0, mesh.drawState().indexCount());
            //?}
        }
        *///?} else {
        type.draw(mesh);
        //?}
    }

    //? if >=26.3 {
    /*/^* Routes {@code type}'s draws into {@code target}, as its output target did before 26.3. ^/
    public static void layer(RenderType type, Supplier<RenderTarget> target) {
        LAYERS.put(type, target);
    }

    static @Nullable Supplier<RenderTarget> layer(RenderType type) {
        return LAYERS.get(type);
    }

    private static RenderTarget layerOf(RenderType type) {
        Supplier<RenderTarget> layer = LAYERS.get(type);
        return layer != null ? layer.get() : Minecraft.getInstance().getMainRenderTarget();
    }
    *///?}
}
//?} else {
/*/^* Before 1.21.11 a draw binds its framebuffer and shader itself; only the depth query is shared. ^/
public final class Passes {
    private Passes() {}

    public static boolean hasDepth(com.mojang.blaze3d.pipeline.RenderTarget target) {
        return target.useDepth;
    }
}
*///?}
