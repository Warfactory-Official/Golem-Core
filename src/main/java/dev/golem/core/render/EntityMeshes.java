package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * The posed cubes of a living entity's main model, in the entity's own space; legacy HBM's
 * {@code ModelRendererUtil.getBoxesFromMob}, which walked the render's {@code ModelBase.boxList} with the matrices
 * {@code RenderLivingBase} would have used ({@code applyRotations}, {@code prepareScale}) from an identity modelview.
 *
 * <p>The entity's own renderer is run once from an identity pose into a recorder, so whatever it does to its
 * model (rotations, scale, baby scale, sitting) matches the frame's transform. Only the main model is kept,
 * chosen the same way on every node: on 26.1 the submission of the renderer's own model object, before 26.1 the
 * first buffer whose render type carries the entity's texture (vanilla {@code LivingEntityRenderer} draws the model
 * before any layer). "The first buffer requested" would be a layer's when the body is skipped (an invisible mob) or
 * an event listener's that drew first. An invisible mob whose body is not drawn yields no cubes on any node.
 *
 * <p>Vanilla cubes emit their six faces in a fixed order, so the vertex stream is cut into cubes of 24 vertices
 * (a convex hexahedron, which the legacy cutter relied on). A cube with fewer faces (1.21+ {@code CubeDefinition}
 * visible faces) would shift every later cut, so each 24-vertex group is validated (at most 8 corners, each used
 * a multiple of 3 times); a group that fails drops one quad and retries, resynchronising on the next whole cube.
 *
 * <p>Client-only.
 */
public final class EntityMeshes {

    /** Floats per recorded vertex: x, y, z, u, v. */
    public static final int STRIDE = 5;
    /** Vertices per vanilla cube: six quads. */
    public static final int CUBE_VERTICES = 24;

    private EntityMeshes() {}

    /**
     * The captured model: its texture and one array per cube, {@value #CUBE_VERTICES} vertices of {@value #STRIDE}
     * floats, quads in emission order, in entity space (origin at the entity's feet, world axes, blocks).
     */
    public record Mesh(
            //? if >=26.1 {
            Identifier texture,
            //?} else {
            /*ResourceLocation texture,
            *///?}
            List<float[]> cubes) {}

    /** The entity's posed main model, or {@code null} when its renderer is not a living-entity renderer. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Mesh capture(LivingEntity entity, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer living)) return null;
        Recorder recorder = new Recorder();
        PoseStack pose = new PoseStack();
        //? if >=26.1 {
        net.minecraft.client.renderer.entity.state.LivingEntityRenderState state =
                (net.minecraft.client.renderer.entity.state.LivingEntityRenderState) living.createRenderState(entity, partialTick);
        Collector collector = new Collector(living.getModel(), recorder);
        living.submit(state, pose, collector, new net.minecraft.client.renderer.state.level.CameraRenderState());
        Identifier texture = living.getTextureLocation(state);
        //?} else {
        /*float yaw = net.minecraft.util.Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(entity, partialTick);
        ResourceLocation texture = living.getTextureLocation(entity);
        Buffers buffers = new Buffers(recorder, texture);
        living.render(entity, yaw, partialTick, pose, buffers, light);
        *///?}
        return new Mesh(texture, recorder.cubes());
    }

    /** Collects positions and texture coordinates; everything else a model writes is dropped. */
    private static final class Recorder implements VertexConsumer {
        private final List<Float> data = new ArrayList<>();
        private float x, y, z, u, v;
        private boolean open;

        private void close() {
            if (!open) return;
            data.add(x); data.add(y); data.add(z); data.add(u); data.add(v);
            open = false;
        }

        List<float[]> cubes() {
            close();
            List<float[]> cubes = new ArrayList<>();
            int floatsPerCube = CUBE_VERTICES * STRIDE;
            int floatsPerQuad = 4 * STRIDE;
            int start = 0;
            while (start + floatsPerCube <= data.size()) {
                if (!isWholeCube(start)) {
                    start += floatsPerQuad; // a partial cube: drop one quad and look for the next whole one
                    continue;
                }
                float[] cube = new float[floatsPerCube];
                for (int i = 0; i < floatsPerCube; i++) cube[i] = data.get(start + i);
                cubes.add(cube);
                start += floatsPerCube;
            }
            return cubes;
        }

        /**
         * Whether the 24 vertices from {@code start} are one box's six faces: a box has at most 8 distinct corners
         * (fewer when flat) and every face meets each of its corners once, so each corner is used a multiple of 3
         * times. Five faces of one cube plus a quad of the next fail both ways.
         */
        private boolean isWholeCube(int start) {
            java.util.Map<Long, Integer> corners = new java.util.HashMap<>();
            for (int vertex = 0; vertex < CUBE_VERTICES; vertex++) {
                int at = start + vertex * STRIDE;
                long key = (Math.round(data.get(at) * 1.0E4) * 73856093L)
                        ^ (Math.round(data.get(at + 1) * 1.0E4) * 19349663L)
                        ^ (Math.round(data.get(at + 2) * 1.0E4) * 83492791L);
                corners.merge(key, 1, Integer::sum);
                if (corners.size() > 8) return false;
            }
            for (int uses : corners.values()) if (uses % 3 != 0) return false;
            return true;
        }

        private VertexConsumer begin(float x, float y, float z) {
            close();
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = 0F;
            this.v = 0F;
            open = true;
            return this;
        }

        //? if >=1.21 {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return begin(x, y, z); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        //?} else {
        /*@Override public VertexConsumer vertex(double x, double y, double z) { return begin((float) x, (float) y, (float) z); }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { close(); }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
        *///?}
        //? if >=26.1 {
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
        //?}
    }

    /** Discards whatever a renderer draws besides its model (layers, name tags, outlines). */
    private static final VertexConsumer SINK = new VertexConsumer() {
        //? if >=1.21 {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        //?} else {
        /*@Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
        *///?}
        //? if >=26.1 {
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
        //?}
    };

    //? if >=26.1 {
    /**
     * Records the renderer's main-model submission. The model is posed and drawn at once, exactly as the feature
     * renderer would draw it later ({@code setupAnim} then {@code renderToBuffer}).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class Collector implements net.minecraft.client.renderer.SubmitNodeCollector {
        private final Object model;
        private final Recorder recorder;
        private boolean taken;

        Collector(Object model, Recorder recorder) {
            this.model = model;
            this.recorder = recorder;
        }

        @Override
        public net.minecraft.client.renderer.OrderedSubmitNodeCollector order(int order) {
            return this;
        }

        @Override
        public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, PoseStack poseStack,
                                    net.minecraft.client.renderer.rendertype.RenderType renderType, int lightCoords,
                                    int overlayCoords, int tintedColor,
                                    net.minecraft.client.renderer.texture.@Nullable TextureAtlasSprite sprite,
                                    int outlineColor,
                                    net.minecraft.client.renderer.feature.ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
            if (taken || model != this.model) return;
            taken = true;
            ((net.minecraft.client.model.Model) model).setupAnim(state);
            model.renderToBuffer(poseStack, recorder, lightCoords, overlayCoords, tintedColor);
        }

        @Override public void submitShadow(PoseStack poseStack, float radius,
                                           List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> pieces) {}
        @Override public void submitNameTag(PoseStack poseStack, net.minecraft.world.phys.@Nullable Vec3 attachment, int offset,
                                            net.minecraft.network.chat.Component name, boolean seeThrough, int lightCoords,
                                            double distanceToCameraSq,
                                            net.minecraft.client.renderer.state.level.CameraRenderState camera) {}
        @Override public void submitText(PoseStack poseStack, float x, float y, net.minecraft.util.FormattedCharSequence string,
                                         boolean dropShadow, net.minecraft.client.gui.Font.DisplayMode displayMode,
                                         int lightCoords, int color, int backgroundColor, int outlineColor) {}
        @Override public void submitFlame(PoseStack poseStack,
                                          net.minecraft.client.renderer.entity.state.EntityRenderState renderState,
                                          org.joml.Quaternionf rotation) {}
        @Override public void submitLeash(PoseStack poseStack,
                                          net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState leashState) {}
        @Override public void submitModelPart(net.minecraft.client.model.geom.ModelPart modelPart, PoseStack poseStack,
                                              net.minecraft.client.renderer.rendertype.RenderType renderType, int lightCoords,
                                              int overlayCoords,
                                              net.minecraft.client.renderer.texture.@Nullable TextureAtlasSprite sprite,
                                              boolean sheeted, boolean hasFoil, int tintedColor,
                                              net.minecraft.client.renderer.feature.ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
                                              int outlineColor) {}
        @Override public void submitMovingBlock(PoseStack poseStack,
                                                net.minecraft.client.renderer.block.MovingBlockRenderState state) {}
        @Override public void submitBlockModel(PoseStack poseStack, net.minecraft.client.renderer.rendertype.RenderType renderType,
                                               List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts,
                                               int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {}
        @Override public void submitBreakingBlockModel(PoseStack poseStack,
                                                       net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
                                                       long seed, int progress) {}
        @Override public void submitItem(PoseStack poseStack, net.minecraft.world.item.ItemDisplayContext displayContext,
                                         int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
                                         List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,
                                         net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foilType) {}
        @Override public void submitCustomGeometry(PoseStack poseStack, net.minecraft.client.renderer.rendertype.RenderType renderType,
                                                   CustomGeometryRenderer customGeometryRenderer) {}
        @Override public void submitParticleGroup(ParticleGroupRenderer particleGroupRenderer) {}
    }
    //?} else {
    /*/^*
     * Hands the model's buffer to the recorder and every other one to the sink. The model's buffer is the first one
     * whose render type uses the entity's own texture: every type {@code LivingEntityRenderer.getRenderType} picks
     * (cutout, translucent, outline when glowing) names it, while armor, held items and most layers use other
     * textures. A skipped body (invisible mob) or a {@code RenderLivingEvent.Pre} listener that draws first is
     * therefore not taken for the model, as in 26.1's model-identity match.
     ^/
    private static final class Buffers implements net.minecraft.client.renderer.MultiBufferSource {
        private final Recorder recorder;
        private final String texture;
        private boolean taken;

        Buffers(Recorder recorder, ResourceLocation texture) {
            this.recorder = recorder;
            this.texture = texture.toString();
        }

        @Override
        public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
            if (taken || !type.toString().contains(texture)) return SINK;
            taken = true;
            return recorder;
        }
    }
    *///?}
}
