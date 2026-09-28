package dev.golem.core.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/** Frame-scoped geometry drawn after world translucency, with the submitting renderer's exact pose. */
public final class WorldEffects {
    /** One submission: geometry into {@code type}'s buffer, or (type null) a text run drawn into the pass's buffers. */
    private record Entry(int order, PoseStack pose, Matrix4f modelView, Runnable drawState, RenderType type,
                         SubmitSink.CustomGeometry geometry,
                         BiConsumer<PoseStack, MultiBufferSource> buffered) {
        Entry(int order, PoseStack pose, Matrix4f modelView, Runnable drawState, RenderType type,
              SubmitSink.CustomGeometry geometry) {
            this(order, pose, modelView, drawState, type, geometry, null);
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static ClientLevel level;
    private static boolean collecting;
    private static boolean registered;
    private static boolean reported;
    private static com.mojang.blaze3d.pipeline.TextureTarget worldDepth;
    private static boolean depthCaptured;
    private static final List<Consumer<CameraInfo>> BEGIN_HOOKS = new ArrayList<>();
    private static final Matrix4f HOOK_MODEL_VIEW = new Matrix4f();
    private static boolean dispatchingHooks;
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();

    private WorldEffects() {}

    public static void init() {
        if (registered) return;
        registered = true;
        dev.golem.core.client.ClientResourceReloads.register("golemcore", "world_effects", () -> {
            clear();
            if (worldDepth != null) worldDepth.destroyBuffers();
            worldDepth = null;
            DepthMerge.close();
        });
        // 26.1 has no loader callback after the transparency framegraph has been composed.
        //? if >=26.1 {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN
                .register(context -> captureDepth());
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks event) -> captureDepth());
        //?}
        //?} else {
        /*//? if fabric {
        //? if >=1.21 {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> begin(context.positionMatrix()));
        //?} else {
        /^net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> begin(context.matrixStack().last().pose()));
        ^///?}
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> captureDepth());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(context -> end());
        //?} elif neoforge {
        /^net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent event) -> {
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) begin(event.getModelViewMatrix());
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) captureDepth();
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) end();
                });
        ^///?} elif forge {
        /^net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderLevelStageEvent event) -> {
                    if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) begin(event.getPoseStack().last().pose());
                    if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) captureDepth();
                    if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) end();
                });
        ^///?}
        *///?}
    }

    /** Save real world depth before the Fabulous fullscreen combine clears/overwrites main depth. */
    private static void captureDepth() {
        if (!isCollecting() || ENTRIES.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var source = minecraft.getMainRenderTarget();
        //? if <26.1 {
        /*int readTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        *///?}
        try {
            if (worldDepth == null || worldDepth.width != source.width || worldDepth.height != source.height) {
                if (worldDepth != null) worldDepth.destroyBuffers();
                //? if >=26.1 {
                worldDepth = new com.mojang.blaze3d.pipeline.TextureTarget("golem_world_effect_depth", source.width, source.height, true);
                //?} else {
                /*worldDepth = new com.mojang.blaze3d.pipeline.TextureTarget(source.width, source.height, true, false);
                *///?}
            }
            // Entity translucency has its own depth attachment in Fabulous. A terrain-only copy
            // loses the player/armor silhouettes; overwriting it with entity depth loses water.
            DepthMerge.merge(worldDepth, source, minecraft.levelRenderer.getTranslucentTarget(),
                    minecraft.levelRenderer.getItemEntityTarget());
            depthCaptured = true;
        } finally {
            //? if <26.1 {
            /*com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readTarget);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            *///?}
        }
    }

    /** 26.1 entry, from the {@code renderLevel} head: the frame's view rotation is the camera state's. */
    public static void begin() {
        //? if >=26.1 {
        begin(Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix);
        //?} else {
        /*begin(new Matrix4f());
        *///?}
    }

    /**
     * Starts a world frame. {@code viewRotation} is the matrix the game renderer applies to
     * camera-relative world coordinates this frame — on 26.1 the camera state's view rotation, on
     * 1.21.1 the loader event's model-view matrix, on 1.20.1 the pose vanilla passes down (which also
     * carries view bob and any loader camera roll). See {@link #viewRotation()}.
     */
    public static void begin(org.joml.Matrix4fc viewRotation) {
        clear();
        VIEW_ROTATION.set(viewRotation);
        level = Minecraft.getInstance().level;
        collecting = level != null;
        if (!collecting) return;
        CameraInfo camera = CameraInfo.current();
        // 1.20.1's Camera.rotation() is rotationYXZ(-yaw, pitch, 0), not the 1.21+ rotationYXZ(pi - yaw, -pitch,
        // roll): its conjugate faces the other way, so every hook submission was drawn behind the camera. The view
        // rotation there is the pose vanilla passed down (pitch, yaw + 180, loader roll), recorded as viewRotation.
        //? if >=1.21 {
        HOOK_MODEL_VIEW.rotation(new Quaternionf(camera.orientation).conjugate());
        //?} else {
        /*HOOK_MODEL_VIEW.set(viewRotation);
        *///?}
        dispatchingHooks = true;
        try {
            for (Consumer<CameraInfo> hook : List.copyOf(BEGIN_HOOKS)) hook.accept(camera);
        } finally {
            dispatchingHooks = false;
        }
    }

    /**
     * The view rotation of the frame being collected.
     *
     * <p>Not for {@link #onBegin} hooks: their submissions are drawn under the hook model-view
     * ({@code HOOK_MODEL_VIEW}), which already is this rotation on every node, so a hook pose only
     * translates by {@code (world - camera.pos)}. Multiplying this in as well rotates the geometry
     * twice (the conveyor-wand and rebar previews were drawn into the sky that way, History
     * §19ed-followups). Submissions made from inside an entity or block-entity pass already sit
     * under the view rotation too.
     */
    public static Matrix4f viewRotation() {
        return new Matrix4f(VIEW_ROTATION);
    }

    public static boolean isCollecting() {
        return collecting && level == Minecraft.getInstance().level;
    }

    /**
     * Registers client geometry that must be submitted at the start of the world frame.
     *
     * <p>A hook runs before the world model-view exists — 1.20 puts the view rotation in the pose
     * stack it hands renderers, and 1.21 onwards pushes it onto the global matrix, but both do so
     * after the world frame has already started. So a hook cannot capture it the way an in-frame
     * renderer does, and {@link #submit} supplies it instead. Poses passed from here are therefore
     * <b>world-axis and camera-relative</b> on every version: translate by world minus
     * {@link CameraInfo#pos}, keep world axes, and billboard by rotating with
     * {@link CameraInfo#orientation}.
     */
    public static void onBegin(Consumer<CameraInfo> callback) {
        BEGIN_HOOKS.add(callback);
    }

    /** Submission order is stable within a priority; GUI/hand previews are never carried into a world frame. */
    public static void submit(PoseStack pose, RenderType type, int order, SubmitSink.CustomGeometry geometry) {
        if (!isCollecting()) return;
        PoseStack copy = new PoseStack();
        copy.last().pose().set(pose.last().pose());
        copy.last().normal().set(pose.last().normal());
        Matrix4f modelView = dispatchingHooks ? new Matrix4f(HOOK_MODEL_VIEW)
                : new Matrix4f(RenderSystem.getModelViewMatrix());
        ENTRIES.add(new Entry(order, copy, modelView, captureDrawState(), type, geometry));
    }

    /**
     * A run of text in this pass, at {@code pose}: the font's own glyph render types (see-through with
     * {@link net.minecraft.client.gui.Font.DisplayMode#SEE_THROUGH}), drawn in submission order among the geometry.
     */
    public static void submitText(PoseStack pose, int order, net.minecraft.client.gui.Font font, String text, float x,
                                  float y, int color, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode,
                                  int backgroundColor, int light) {
        if (!isCollecting()) return;
        PoseStack copy = new PoseStack();
        copy.last().pose().set(pose.last().pose());
        copy.last().normal().set(pose.last().normal());
        Matrix4f modelView = dispatchingHooks ? new Matrix4f(HOOK_MODEL_VIEW)
                : new Matrix4f(RenderSystem.getModelViewMatrix());
        ENTRIES.add(new Entry(order, copy, modelView, captureDrawState(), null, null, (drawPose, buffers) ->
                font.drawInBatch(text, x, y, color, shadow, drawPose.last().pose(), buffers, mode, backgroundColor, light)));
    }

    private static Runnable captureDrawState() {
        //? if >=26.1 {
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var projectionType = RenderSystem.getProjectionType();
        var fog = RenderSystem.getShaderFog();
        return () -> {
            RenderSystem.setProjectionMatrix(projection, projectionType);
            RenderSystem.setShaderFog(fog);
        };
        //?} else {
        /*Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        var fogShape = RenderSystem.getShaderFogShape();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        float[] color = RenderSystem.getShaderColor().clone();
        return () -> {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        };
        *///?}
    }

    /**
     * Routes a declared model's geometry into this pass, so a {@code Gem} model can be replayed here
     * the way {@code CompatBER} replays one into the world frame.
     *
     * <p>Only geometry is carried. An item, a text run or a block model has no business in a late
     * effects pass — each of those wants the world's own batching and ordering, which this pass has
     * already ended by the time it draws.
     */
    public static SubmitSink sink(int order) {
        return new EffectSink(order);
    }

    private record EffectSink(int order) implements SubmitSink {

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                         SubmitSink.CustomGeometry geometry) {
            submit(poseStack, renderType, order, geometry);
        }

        @Override
        public SubmitSink order(int order) {
            return order == this.order ? this : new EffectSink(order);
        }

        @Override
        public void submitItem(PoseStack poseStack, CompatItemRenderState item, int light, int overlay, int seed) {
            throw new UnsupportedOperationException("World effects draw geometry only");
        }

        @Override
        public void submitBlock(PoseStack poseStack, CompatBlockRenderState block, int light, int overlay) {
            throw new UnsupportedOperationException("World effects draw geometry only");
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, CompatMovingBlock block) {
            throw new UnsupportedOperationException("World effects draw geometry only");
        }

        @Override
        public void submitBlockEntity(PoseStack poseStack, CompatBlockEntityRenderState blockEntity) {
            throw new UnsupportedOperationException("World effects draw geometry only");
        }

        @Override
        public void submitText(PoseStack poseStack, net.minecraft.client.gui.Font font, float x, float y,
                               net.minecraft.util.FormattedCharSequence text, boolean shadow,
                               net.minecraft.client.gui.Font.DisplayMode mode, int light, int color,
                               int backgroundColor) {
            throw new UnsupportedOperationException("World effects draw geometry only");
        }
    }

    public static void clear() {
        ENTRIES.clear();
        collecting = false;
        depthCaptured = false;
        level = null;
    }

    /** Called before the world projection/depth are replaced for first-person hands or GUI rendering. */
    public static void end() {
        Runnable restoreState = null;
        try {
            if (!isCollecting()) return;
            if (ENTRIES.isEmpty()) return;
            collecting = false;
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            buffers.endBatch();
            if (!depthCaptured) throw new IllegalStateException("World effects submitted without world depth capture");
            Minecraft.getInstance().getMainRenderTarget().copyDepthFrom(worldDepth);
            //? if <26.1 {
            /*Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            *///?}
            ENTRIES.sort(Comparator.comparingInt(Entry::order));
            restoreState = captureDrawState();
            // Before 26.1 a render type's NO_DEPTH_TEST shard does nothing: vanilla's DepthTestStateShard only
            // enables/disables for functions other than ALWAYS, relying on each type's clear leaving the depth
            // test off. At the level-end event it is still on, so every see-through type was depth tested.
            // Start from that between-types default, and put the event's state back afterwards.
            //? if <26.1 {
            /*boolean depthWasOn = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            RenderSystem.disableDepthTest();
            Runnable drawState = restoreState;
            restoreState = () -> {
                drawState.run();
                if (depthWasOn) RenderSystem.enableDepthTest();
                else RenderSystem.disableDepthTest();
            };
            *///?}
            boolean captured = false;
            for (Entry entry : ENTRIES) {
                if (!captured && entry.type() != null && HeatDistortionRenderType.isSceneSampling(entry.type())) {
                    HeatDistortionRenderType.prepareSceneForPass();
                    captured = true;
                }
                entry.drawState().run();
                // Keep the world view even when a loader's last-world event restores its global stack.
                //? if >=1.21 {
                RenderSystem.getModelViewStack().pushMatrix().set(entry.modelView());
                //? if <26.1 {
                /*RenderSystem.applyModelViewMatrix();
                *///?}
                //?} else {
                /*RenderSystem.getModelViewStack().pushPose();
                RenderSystem.getModelViewStack().last().pose().set(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                *///?}
                try {
                    if (entry.buffered() != null) entry.buffered().accept(entry.pose(), buffers);
                    else entry.geometry().render(entry.pose().last(), buffers.getBuffer(entry.type()));
                    buffers.endBatch();
                } finally {
                    //? if >=1.21 {
                    RenderSystem.getModelViewStack().popMatrix();
                    //? if <26.1 {
                    /*RenderSystem.applyModelViewMatrix();
                    *///?}
                    //?} else {
                    /*RenderSystem.getModelViewStack().popPose();
                    RenderSystem.applyModelViewMatrix();
                    *///?}
                }
            }
            if (!reported) {
                reported = true;
                dev.golem.core.GolemCore.LOG.info("World effects rendered after translucent composition ({} submissions)", ENTRIES.size());
            }
        } finally {
            try {
                if (restoreState != null) restoreState.run();
            } finally {
                clear();
            }
        }
    }
}
